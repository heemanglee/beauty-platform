package com.beautyplatform.product

import com.beautyplatform.auth.dto.AuthTokenResponse
import com.beautyplatform.auth.dto.LoginRequest
import com.beautyplatform.category.repository.CategoryRepository
import com.beautyplatform.common.security.SecurityTestProbeConfiguration
import com.beautyplatform.product.dto.CreateProductRequest
import com.beautyplatform.product.dto.IssueProductImageUploadUrlRequest
import com.beautyplatform.product.dto.ProductImageGroupRequest
import com.beautyplatform.product.dto.ProductImageItemRequest
import com.beautyplatform.product.enums.ProductImageType
import com.beautyplatform.product.repository.ProductRepository
import com.beautyplatform.user.entity.User
import com.beautyplatform.user.enums.UserRole
import com.beautyplatform.user.repository.UserRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
class BuyerCatalogDiscoveryIntegrationTest(
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
    fun `unauthenticated user can list ON_SALE products with purchasable true`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        createSaleReadyProduct(sellerToken, categoryId, "Glow Serum", 32_000)

        mockMvc
            .perform(get("/api/public/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Glow Serum"))
            .andExpect(jsonPath("$[0].price").value(32_000))
            .andExpect(jsonPath("$[0].sellerId").isNumber)
            .andExpect(jsonPath("$[0].sellerName").value("Seller"))
            .andExpect(jsonPath("$[0].status").value("ON_SALE"))
            .andExpect(jsonPath("$[0].purchasable").value(true))
            .andExpect(jsonPath("$[0].thumbnailImageUrl").exists())
    }

    @Test
    fun `unauthenticated user can list SOLD_OUT products with purchasable false`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        val productId = createSaleReadyProduct(sellerToken, categoryId, "Limited Edition Cream", 50_000)

        mockMvc
            .perform(
                put("/api/seller/products/{productId}", productId)
                    .header("Authorization", "Bearer ${sellerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            productRequest(
                                categoryId = categoryId,
                                name = "Limited Edition Cream",
                                price = 50_000,
                                stockQuantity = 0,
                                thumbnailKeys = listOf(issueUploadUrl(sellerToken, productId, ProductImageType.THUMBNAIL)["s3Key"].asText()),
                                mainKeys = listOf(issueUploadUrl(sellerToken, productId, ProductImageType.MAIN)["s3Key"].asText()),
                                descriptionKeys = listOf(issueUploadUrl(sellerToken, productId, ProductImageType.DESCRIPTION)["s3Key"].asText()),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SOLD_OUT"))

        mockMvc
            .perform(get("/api/public/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Limited Edition Cream"))
            .andExpect(jsonPath("$[0].status").value("SOLD_OUT"))
            .andExpect(jsonPath("$[0].purchasable").value(false))
    }

    @Test
    fun `PENDING products are not exposed in public list`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        createProduct(sellerToken, categoryId, "Draft Product", 10_000, 5)

        mockMvc
            .perform(get("/api/public/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `BANNED products are not exposed in public list`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        val productId = createSaleReadyProduct(sellerToken, categoryId, "Banned Item", 20_000)

        mockMvc
            .perform(
                patch("/api/admin/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"BANNED"}"""),
            ).andExpect(status().isOk)

        mockMvc
            .perform(get("/api/public/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `PENDING product direct access returns 404`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        val productId = createProduct(sellerToken, categoryId, "Pending Item", 15_000, 3)

        mockMvc
            .perform(get("/api/public/products/{productId}", productId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `BANNED product direct access returns 404`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        val productId = createSaleReadyProduct(sellerToken, categoryId, "Banned Direct", 25_000)

        mockMvc
            .perform(
                patch("/api/admin/products/{productId}/status", productId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"BANNED"}"""),
            ).andExpect(status().isOk)

        mockMvc
            .perform(get("/api/public/products/{productId}", productId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `products can be filtered by seller`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val categoryId = createCategory(adminToken, "Skincare")

        createSellerUser("seller.a@beauty.local", "seller-pass-123", "010-8888-0002")
        createSellerUser("seller.b@beauty.local", "seller-pass-123", "010-8888-0003")
        val sellerAToken = login("seller.a@beauty.local", "seller-pass-123")
        val sellerBToken = login("seller.b@beauty.local", "seller-pass-123")

        createSaleReadyProduct(sellerAToken, categoryId, "Seller A Product", 10_000)
        createSaleReadyProduct(sellerBToken, categoryId, "Seller B Product", 20_000)

        val sellerAId =
            objectMapper.readTree(
                mockMvc
                    .perform(get("/api/public/products"))
                    .andReturn()
                    .response.contentAsByteArray,
            ).first { it["name"].asText() == "Seller A Product" }["sellerId"].asLong()

        mockMvc
            .perform(get("/api/public/products").param("sellerId", sellerAId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Seller A Product"))
            .andExpect(jsonPath("$[0].sellerId").value(sellerAId))
    }

    @Test
    fun `products can be filtered by category`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val (sellerToken, skincareId) = setUpSellerAndCategory()
        val makeupId = createCategory(adminToken, "Makeup")

        createSaleReadyProduct(sellerToken, skincareId, "Toner", 18_000)
        createSaleReadyProduct(sellerToken, makeupId, "Lip Tint", 22_000)

        mockMvc
            .perform(get("/api/public/products").param("categoryId", skincareId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Toner"))

        mockMvc
            .perform(get("/api/public/products").param("categoryId", makeupId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Lip Tint"))
    }

    @Test
    fun `products can be searched by keyword with partial name matching`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        createSaleReadyProduct(sellerToken, categoryId, "가나 다라마", 10_000)
        createSaleReadyProduct(sellerToken, categoryId, "바사 아자차", 20_000)

        mockMvc
            .perform(get("/api/public/products").param("keyword", "가나"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("가나 다라마"))
    }

    @Test
    fun `products can be filtered by category and keyword combined`() {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val (sellerToken, skincareId) = setUpSellerAndCategory()
        val makeupId = createCategory(adminToken, "Makeup")

        createSaleReadyProduct(sellerToken, skincareId, "Glow Serum", 32_000)
        createSaleReadyProduct(sellerToken, skincareId, "Glow Cream", 28_000)
        createSaleReadyProduct(sellerToken, makeupId, "Glow Lip", 15_000)

        mockMvc
            .perform(
                get("/api/public/products")
                    .param("categoryId", skincareId.toString())
                    .param("keyword", "Glow"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `default sort is LATEST with newest product first`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        createSaleReadyProduct(sellerToken, categoryId, "First Product", 10_000)
        createSaleReadyProduct(sellerToken, categoryId, "Second Product", 20_000)

        mockMvc
            .perform(get("/api/public/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Second Product"))
            .andExpect(jsonPath("$[1].name").value("First Product"))
    }

    @Test
    fun `products can be sorted by price ascending`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        createSaleReadyProduct(sellerToken, categoryId, "Expensive", 50_000)
        createSaleReadyProduct(sellerToken, categoryId, "Cheap", 10_000)

        mockMvc
            .perform(get("/api/public/products").param("sort", "PRICE_ASC"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Cheap"))
            .andExpect(jsonPath("$[1].name").value("Expensive"))
    }

    @Test
    fun `products can be sorted by price descending`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        createSaleReadyProduct(sellerToken, categoryId, "Expensive", 50_000)
        createSaleReadyProduct(sellerToken, categoryId, "Cheap", 10_000)

        mockMvc
            .perform(get("/api/public/products").param("sort", "PRICE_DESC"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Expensive"))
            .andExpect(jsonPath("$[1].name").value("Cheap"))
    }

    @Test
    fun `product detail includes all image groups`() {
        val (sellerToken, categoryId) = setUpSellerAndCategory()
        val productId = createSaleReadyProduct(sellerToken, categoryId, "Detail Product", 35_000)

        mockMvc
            .perform(get("/api/public/products/{productId}", productId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Detail Product"))
            .andExpect(jsonPath("$.price").value(35_000))
            .andExpect(jsonPath("$.sellerName").value("Seller"))
            .andExpect(jsonPath("$.purchasable").value(true))
            .andExpect(jsonPath("$.images.thumbnailImages").isNotEmpty)
            .andExpect(jsonPath("$.images.mainImages").isNotEmpty)
            .andExpect(jsonPath("$.images.descriptionImages").isNotEmpty)
    }

    private data class SellerSetup(
        val sellerToken: AuthTokenResponse,
        val categoryId: Long,
    )

    private fun setUpSellerAndCategory(): SellerSetup {
        val adminToken = login("admin@beauty.local", "AdminPass123!")
        val categoryId = createCategory(adminToken, "Skincare")
        createSellerUser("seller@beauty.local", "seller-pass-123", "010-8888-0001")
        val sellerToken = login("seller@beauty.local", "seller-pass-123")
        return SellerSetup(sellerToken, categoryId)
    }

    private fun createSaleReadyProduct(
        sellerToken: AuthTokenResponse,
        categoryId: Long,
        name: String,
        price: Long,
    ): Long {
        val productId = createProduct(sellerToken, categoryId, name, price, 7)
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
                                price = price,
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
