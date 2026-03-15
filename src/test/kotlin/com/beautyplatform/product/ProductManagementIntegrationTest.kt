package com.beautyplatform.product

import com.beautyplatform.auth.dto.AuthTokenResponse
import com.beautyplatform.auth.dto.LoginRequest
import com.beautyplatform.category.CategoryRepository
import com.beautyplatform.common.security.SecurityTestProbeConfiguration
import com.beautyplatform.product.dto.CreateProductRequest
import com.beautyplatform.product.dto.IssueProductImageUploadUrlRequest
import com.beautyplatform.product.dto.ProductImageGroupRequest
import com.beautyplatform.product.dto.ProductImageItemRequest
import com.beautyplatform.product.entity.ProductImage
import com.beautyplatform.product.enums.ProductImageType
import com.beautyplatform.product.repository.ProductRepository
import com.beautyplatform.user.User
import com.beautyplatform.user.UserRepository
import com.beautyplatform.user.UserRole
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityTestProbeConfiguration::class, ProductImageStorageTestConfiguration::class)
@Testcontainers
class ProductManagementIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val categoryRepository: CategoryRepository,
    @Autowired private val productRepository: ProductRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val productImageStorage: TestProductImageStorage,
) {
    @BeforeEach
    fun setUp() {
        productRepository.deleteAll()
        categoryRepository.deleteAll()
        productImageStorage.reset()

        val removableIds =
            userRepository
                .findAll()
                .filter { it.role != UserRole.ADMIN }
                .mapNotNull { it.id }
        if (removableIds.isNotEmpty()) {
            userRepository.deleteAllById(removableIds)
        }
    }

    @Test
    fun `seller can create product add images move to on sale and admin can inspect it`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val categoryId = createCategory(adminToken, "Skincare")
        createSellerUser("seller.one@beauty.local", "seller-pass-123", "010-7777-0001")
        val sellerToken = login("seller.one@beauty.local", "seller-pass-123")

        val productId = createProduct(sellerToken, categoryId, "Glow Serum", 32_000, 10)

        val thumbnailKey = issueUploadUrl(sellerToken, productId, ProductImageType.THUMBNAIL)["s3Key"].asText()
        val mainKey = issueUploadUrl(sellerToken, productId, ProductImageType.MAIN)["s3Key"].asText()
        val descriptionKey = issueUploadUrl(sellerToken, productId, ProductImageType.DESCRIPTION)["s3Key"].asText()

        mockMvc
            .perform(
                put("/api/seller/products/{productId}", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            productRequest(
                                categoryId = categoryId,
                                name = "Glow Serum",
                                price = 32_000,
                                stockQuantity = 10,
                                thumbnailKeys = listOf(thumbnailKey),
                                mainKeys = listOf(mainKey),
                                descriptionKeys = listOf(descriptionKey),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.images.thumbnailImages[0].url").value("https://cdn.test.example.com/$thumbnailKey"))

        mockMvc
            .perform(
                patch("/api/seller/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ON_SALE"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ON_SALE"))

        mockMvc
            .perform(
                get("/api/seller/products")
                    .header("Authorization", "Bearer ${sellerToken.accessToken}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Glow Serum"))
            .andExpect(jsonPath("$[0].thumbnailImageUrl").value("https://cdn.test.example.com/$thumbnailKey"))
            .andExpect(jsonPath("$[0].status").value("ON_SALE"))

        mockMvc
            .perform(
                get("/api/admin/products/{productId}", productId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.sellerId").value(sellerToken.user.id))
            .andExpect(jsonPath("$.images.mainImages[0].url").value("https://cdn.test.example.com/$mainKey"))
    }

    @Test
    fun `seller cannot read or mutate another sellers product`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val categoryId = createCategory(adminToken, "Makeup")
        createSellerUser("seller.owner@beauty.local", "seller-pass-123", "010-7777-0002")
        createSellerUser("seller.other@beauty.local", "seller-pass-123", "010-7777-0003")
        val ownerToken = login("seller.owner@beauty.local", "seller-pass-123")
        val otherToken = login("seller.other@beauty.local", "seller-pass-123")

        val productId = createProduct(ownerToken, categoryId, "Lip Tint", 18_000, 5)

        mockMvc
            .perform(
                get("/api/seller/products/{productId}", productId)
                    .header("Authorization", "Bearer ${otherToken.accessToken}"),
            ).andExpect(status().isNotFound)

        mockMvc
            .perform(
                put("/api/seller/products/{productId}", productId)
                    .header("Authorization", "Bearer ${otherToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(productRequest(categoryId, "Lip Tint", 18_000, 5))),
            ).andExpect(status().isNotFound)

        mockMvc
            .perform(
                post("/api/seller/products/{productId}/image-upload-url", productId)
                    .header("Authorization", "Bearer ${otherToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            IssueProductImageUploadUrlRequest(
                                type = ProductImageType.THUMBNAIL,
                                filename = "thumb.jpg",
                                contentType = "image/jpeg",
                                contentLength = 1024,
                            ),
                        ),
                    ),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `seller cannot move product to on sale without required images or stock`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val categoryId = createCategory(adminToken, "Haircare")
        createSellerUser("seller.stock@beauty.local", "seller-pass-123", "010-7777-0004")
        val sellerToken = login("seller.stock@beauty.local", "seller-pass-123")

        val productId = createProduct(sellerToken, categoryId, "Hair Oil", 24_000, 10)

        mockMvc
            .perform(
                patch("/api/seller/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ON_SALE"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Product must have at least one THUMBNAIL image to move to ON_SALE"))

        val thumbnailKey = issueUploadUrl(sellerToken, productId, ProductImageType.THUMBNAIL)["s3Key"].asText()
        val mainKey = issueUploadUrl(sellerToken, productId, ProductImageType.MAIN)["s3Key"].asText()
        val descriptionKey = issueUploadUrl(sellerToken, productId, ProductImageType.DESCRIPTION)["s3Key"].asText()

        mockMvc
            .perform(
                put("/api/seller/products/{productId}", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            productRequest(
                                categoryId = categoryId,
                                name = "Hair Oil",
                                price = 24_000,
                                stockQuantity = 0,
                                thumbnailKeys = listOf(thumbnailKey),
                                mainKeys = listOf(mainKey),
                                descriptionKeys = listOf(descriptionKey),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))

        mockMvc
            .perform(
                patch("/api/seller/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ON_SALE"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Product with zero stock cannot be moved to ON_SALE"))
    }

    @Test
    fun `admin can ban product and seller cannot reactivate it`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val categoryId = createCategory(adminToken, "Bodycare")
        createSellerUser("seller.ban@beauty.local", "seller-pass-123", "010-7777-0005")
        val sellerToken = login("seller.ban@beauty.local", "seller-pass-123")

        val productId = createSaleReadyProduct(sellerToken, categoryId, "Body Lotion")

        mockMvc
            .perform(
                patch("/api/seller/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"BANNED"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Seller can only change product status to ON_SALE"))

        mockMvc
            .perform(
                patch("/api/admin/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ON_SALE"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Admin can only change product status to BANNED"))

        mockMvc
            .perform(
                patch("/api/admin/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"BANNED"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("BANNED"))

        mockMvc
            .perform(
                patch("/api/seller/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ON_SALE"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("BANNED product cannot be moved to ON_SALE by seller"))
    }

    @Test
    fun `image replacement failure rolls back product update`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val categoryId = createCategory(adminToken, "Fragrance")
        createSellerUser("seller.rollback@beauty.local", "seller-pass-123", "010-7777-0006")
        val sellerToken = login("seller.rollback@beauty.local", "seller-pass-123")

        val productId = createProduct(sellerToken, categoryId, "Perfume", 58_000, 8)
        val oldThumbnailKey = issueUploadUrl(sellerToken, productId, ProductImageType.THUMBNAIL)["s3Key"].asText()
        val oldMainKey = issueUploadUrl(sellerToken, productId, ProductImageType.MAIN)["s3Key"].asText()
        val oldDescriptionKey = issueUploadUrl(sellerToken, productId, ProductImageType.DESCRIPTION)["s3Key"].asText()

        mockMvc
            .perform(
                put("/api/seller/products/{productId}", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            productRequest(
                                categoryId = categoryId,
                                name = "Perfume",
                                price = 58_000,
                                stockQuantity = 8,
                                thumbnailKeys = listOf(oldThumbnailKey),
                                mainKeys = listOf(oldMainKey),
                                descriptionKeys = listOf(oldDescriptionKey),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)

        val newThumbnailKey = issueUploadUrl(sellerToken, productId, ProductImageType.THUMBNAIL)["s3Key"].asText()
        val newMainKey = issueUploadUrl(sellerToken, productId, ProductImageType.MAIN)["s3Key"].asText()
        val newDescriptionKey = issueUploadUrl(sellerToken, productId, ProductImageType.DESCRIPTION)["s3Key"].asText()
        productImageStorage.failOnDelete(oldThumbnailKey)

        mockMvc
            .perform(
                put("/api/seller/products/{productId}", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            productRequest(
                                categoryId = categoryId,
                                name = "Updated Perfume",
                                price = 63_000,
                                stockQuantity = 8,
                                thumbnailKeys = listOf(newThumbnailKey),
                                mainKeys = listOf(newMainKey),
                                descriptionKeys = listOf(newDescriptionKey),
                            ),
                        ),
                    ),
            ).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Failed to delete product image: $oldThumbnailKey"))

        val persistedProduct = productRepository.findOwnedWithImages(productId, sellerToken.user.id)
        assertThat(persistedProduct).isNotNull
        assertThat(persistedProduct!!.name).isEqualTo("Perfume")
        assertThat(persistedProduct.images.map(ProductImage::s3Key)).containsExactlyInAnyOrder(oldThumbnailKey, oldMainKey, oldDescriptionKey)
    }

    private fun createSaleReadyProduct(
        sellerToken: AuthTokenResponse,
        categoryId: Long,
        name: String,
    ): Long {
        val productId = createProduct(sellerToken, categoryId, name, 29_000, 7)
        val thumbnailKey = issueUploadUrl(sellerToken, productId, ProductImageType.THUMBNAIL)["s3Key"].asText()
        val mainKey = issueUploadUrl(sellerToken, productId, ProductImageType.MAIN)["s3Key"].asText()
        val descriptionKey = issueUploadUrl(sellerToken, productId, ProductImageType.DESCRIPTION)["s3Key"].asText()

        mockMvc
            .perform(
                put("/api/seller/products/{productId}", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            productRequest(
                                categoryId = categoryId,
                                name = name,
                                price = 29_000,
                                stockQuantity = 7,
                                thumbnailKeys = listOf(thumbnailKey),
                                mainKeys = listOf(mainKey),
                                descriptionKeys = listOf(descriptionKey),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                patch("/api/seller/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ON_SALE"}"""),
            ).andExpect(status().isOk)

        return productId
    }

    private fun createProduct(
        sellerToken: AuthTokenResponse,
        categoryId: Long,
        name: String,
        price: Long,
        stockQuantity: Int,
    ): Long {
        val response =
            mockMvc
                .perform(
                    post("/api/seller/products")
                        .header("Authorization", "Bearer ${sellerToken.accessToken}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsBytes(
                                productRequest(
                                    categoryId = categoryId,
                                    name = name,
                                    price = price,
                                    stockQuantity = stockQuantity,
                                ),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .response

        return objectMapper.readTree(response.contentAsByteArray)["id"].asLong()
    }

    private fun issueUploadUrl(
        sellerToken: AuthTokenResponse,
        productId: Long,
        type: ProductImageType,
    ): JsonNode {
        val response =
            mockMvc
                .perform(
                    post("/api/seller/products/{productId}/image-upload-url", productId)
                        .header("Authorization", "Bearer ${sellerToken.accessToken}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsBytes(
                                IssueProductImageUploadUrlRequest(
                                    type = type,
                                    filename = "${type.name.lowercase()}.jpg",
                                    contentType = "image/jpeg",
                                    contentLength = 1024,
                                ),
                            ),
                        ),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.s3Key").value(org.hamcrest.Matchers.startsWith("product_images/$productId/")))
                .andReturn()
                .response

        return objectMapper.readTree(response.contentAsByteArray)
    }

    private fun productRequest(
        categoryId: Long,
        name: String,
        price: Long,
        stockQuantity: Int,
        thumbnailKeys: List<String> = emptyList(),
        mainKeys: List<String> = emptyList(),
        descriptionKeys: List<String> = emptyList(),
    ): CreateProductRequest =
        CreateProductRequest(
            name = name,
            price = price,
            stockQuantity = stockQuantity,
            categoryId = categoryId,
            images =
                ProductImageGroupRequest(
                    thumbnailImages = thumbnailKeys.mapIndexed { index, key -> ProductImageItemRequest(s3Key = key, sortOrder = index + 1) },
                    mainImages = mainKeys.mapIndexed { index, key -> ProductImageItemRequest(s3Key = key, sortOrder = index + 1) },
                    descriptionImages = descriptionKeys.mapIndexed { index, key -> ProductImageItemRequest(s3Key = key, sortOrder = index + 1) },
                ),
        )

    private fun createCategory(
        adminToken: AuthTokenResponse,
        name: String,
    ): Long {
        val response =
            mockMvc
                .perform(
                    post("/api/admin/categories")
                        .header("Authorization", "Bearer ${adminToken.accessToken}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(mapOf("name" to name))),
                ).andExpect(status().isCreated)
                .andReturn()
                .response

        return objectMapper.readTree(response.contentAsByteArray)["id"].asLong()
    }

    private fun createSellerUser(
        email: String,
        password: String,
        phoneNumber: String,
    ) {
        userRepository.save(
            User(
                role = UserRole.SELLER,
                name = "Seller",
                email = email,
                passwordHash = passwordEncoder.encode(password),
                phoneNumber = phoneNumber,
            ),
        )
    }

    private fun login(
        email: String,
        password: String,
    ): AuthTokenResponse {
        val response =
            mockMvc
                .perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(LoginRequest(email = email, password = password))),
                ).andExpect(status().isOk)
                .andReturn()
                .response

        return objectMapper.readValue(response.contentAsByteArray, AuthTokenResponse::class.java)
    }

    companion object {
        @Container
        @JvmStatic
        private val mysql = MySQLContainer("mysql:8.4.6")

        init {
            System.setProperty("ADMIN_EMAIL", "admin@beauty.local")
            System.setProperty("ADMIN_PASSWORD", "AdminPass123!")
            System.setProperty("ADMIN_NAME", "Platform Admin")
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
}
