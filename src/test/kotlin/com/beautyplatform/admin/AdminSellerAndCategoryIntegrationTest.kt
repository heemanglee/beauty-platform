package com.beautyplatform.admin

import com.beautyplatform.auth.dto.AuthTokenResponse
import com.beautyplatform.auth.dto.LoginRequest
import com.beautyplatform.category.CategoryRepository
import com.beautyplatform.category.CategoryUsageGuard
import com.beautyplatform.common.security.SecurityTestProbeConfiguration
import com.beautyplatform.seller.AdminCreateSellerRequest
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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.MediaType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityTestProbeConfiguration::class, CategoryUsageGuardTestConfiguration::class)
@Testcontainers
class AdminSellerAndCategoryIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val categoryRepository: CategoryRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val categoryUsageGuard: TestCategoryUsageGuard,
) {
    @BeforeEach
    fun setUp() {
        categoryRepository.deleteAll()
        categoryUsageGuard.reset()

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
    fun `admin can create seller and created seller can login immediately`() {
        val adminToken = login(email = "admin@beauty.local", password = "AdminPass123!")

        val response =
            mockMvc
                .perform(
                    post("/api/admin/sellers")
                        .header("Authorization", "Bearer ${adminToken.accessToken}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsBytes(
                                AdminCreateSellerRequest(
                                    name = "Seller One",
                                    email = "Seller.One@Example.COM",
                                    password = "seller-pass-123",
                                    phoneNumber = "010-2222-3333",
                                ),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.role").value("SELLER"))
                .andExpect(jsonPath("$.email").value("seller.one@example.com"))
                .andReturn()
                .response

        val savedSeller = userRepository.findByEmail("seller.one@example.com")
        assertThat(savedSeller).isNotNull
        assertThat(savedSeller!!.role).isEqualTo(UserRole.SELLER)
        assertThat(savedSeller.phoneNumber).isEqualTo("010-2222-3333")
        assertThat(passwordEncoder.matches("seller-pass-123", savedSeller.passwordHash)).isTrue()

        val sellerLogin = login(email = "seller.one@example.com", password = "seller-pass-123")

        assertThat(sellerLogin.user.role).isEqualTo(UserRole.SELLER)
        assertThat(response.contentAsString).contains("seller.one@example.com")
    }

    @Test
    fun `seller creation rejects duplicate email and phone number across roles`() {
        signupBuyer(email = "buyer@beauty.local", password = "buyer-pass-123", phoneNumber = "010-9999-8888")
        val adminToken = login(email = "admin@beauty.local", password = "AdminPass123!")

        mockMvc
            .perform(
                post("/api/admin/sellers")
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            AdminCreateSellerRequest(
                                name = "Seller One",
                                email = "buyer@beauty.local",
                                password = "seller-pass-123",
                                phoneNumber = "010-2222-3333",
                            ),
                        ),
                    ),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Email already exists: buyer@beauty.local"))

        mockMvc
            .perform(
                post("/api/admin/sellers")
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            AdminCreateSellerRequest(
                                name = "Seller Two",
                                email = "seller2@beauty.local",
                                password = "seller-pass-123",
                                phoneNumber = "010-9999-8888",
                            ),
                        ),
                    ),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Phone number already exists: 010-9999-8888"))
    }

    @Test
    fun `non admin cannot create seller`() {
        signupBuyer(email = "buyer@beauty.local", password = "buyer-pass-123")
        val buyerToken = login(email = "buyer@beauty.local", password = "buyer-pass-123")

        mockMvc
            .perform(
                post("/api/admin/sellers")
                    .header("Authorization", "Bearer ${buyerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            AdminCreateSellerRequest(
                                name = "Seller One",
                                email = "seller@beauty.local",
                                password = "seller-pass-123",
                                phoneNumber = "010-2222-3333",
                            ),
                        ),
                    ),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `seller creation returns field level error when required password field is missing`() {
        val adminToken = login(email = "admin@beauty.local", password = "AdminPass123!")

        mockMvc
            .perform(
                post("/api/admin/sellers")
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "name": "판매자-A",
                            "email": "seller-a@gmail.com",
                            "passowrd": "Dlgmlakd01!@",
                            "phoneNumber": "010-1111-1111"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.validationErrors[0].field").value("password"))
            .andExpect(jsonPath("$.validationErrors[0].message").value("Required field is missing"))
    }

    @Test
    fun `admin category CRUD records audit metadata and seller can list categories`() {
        val adminToken = login(email = "admin@beauty.local", password = "AdminPass123!")
        val adminId = adminToken.user.id

        val createResponse =
            mockMvc
                .perform(
                    post("/api/admin/categories")
                        .header("Authorization", "Bearer ${adminToken.accessToken}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Skincare"}"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("Skincare"))
                .andReturn()
                .response

        val categoryId = objectMapper.readTree(createResponse.contentAsByteArray)["id"].asLong()
        val createdCategory = categoryRepository.findByIdOrNull(categoryId) ?: error("Category not found after create: $categoryId")
        assertThat(createdCategory.createdByUserId).isEqualTo(adminId)
        assertThat(createdCategory.updatedByUserId).isEqualTo(adminId)
        assertThat(createdCategory.createdAt).isNotNull
        assertThat(createdCategory.updatedAt).isNotNull

        val secondAdmin =
            userRepository.save(
                User(
                    role = UserRole.ADMIN,
                    name = "Second Admin",
                    email = "admin2@beauty.local",
                    passwordHash = passwordEncoder.encode("AdminPass456!"),
                    phoneNumber = "010-5555-6666",
                ),
            )
        val secondAdminToken = login(email = "admin2@beauty.local", password = "AdminPass456!")

        mockMvc
            .perform(
                put("/api/admin/categories/{categoryId}", categoryId)
                    .header("Authorization", "Bearer ${secondAdminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Premium Skincare"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Premium Skincare"))

        val updatedCategory = categoryRepository.findByIdOrNull(categoryId) ?: error("Category not found after update: $categoryId")
        assertThat(updatedCategory.name).isEqualTo("Premium Skincare")
        assertThat(updatedCategory.createdByUserId).isEqualTo(adminId)
        assertThat(updatedCategory.updatedByUserId).isEqualTo(secondAdmin.id)
        assertThat(updatedCategory.updatedAt).isAfterOrEqualTo(updatedCategory.createdAt ?: Instant.EPOCH)

        mockMvc
            .perform(
                get("/api/admin/categories")
                    .header("Authorization", "Bearer ${adminToken.accessToken}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Premium Skincare"))

        userRepository.save(
            User(
                role = UserRole.SELLER,
                name = "Seller One",
                email = "seller@beauty.local",
                passwordHash = passwordEncoder.encode("seller-pass-123"),
                phoneNumber = "010-7777-8888",
            ),
        )
        val sellerToken = login(email = "seller@beauty.local", password = "seller-pass-123")

        mockMvc
            .perform(
                get("/api/seller/categories")
                    .header("Authorization", "Bearer ${sellerToken.accessToken}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Premium Skincare"))

        mockMvc
            .perform(
                delete("/api/admin/categories/{categoryId}", categoryId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}"),
            ).andExpect(status().isNoContent)

        assertThat(categoryRepository.findById(categoryId)).isEmpty
    }

    @Test
    fun `category create and update reject duplicate names`() {
        val adminToken = login(email = "admin@beauty.local", password = "AdminPass123!")

        val firstCategoryId = createCategory(adminToken, "Skincare")
        createCategory(adminToken, "Makeup")

        mockMvc
            .perform(
                post("/api/admin/categories")
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Skincare"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Category already exists: Skincare"))

        mockMvc
            .perform(
                put("/api/admin/categories/{categoryId}", firstCategoryId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Makeup"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Category already exists: Makeup"))
    }

    @Test
    fun `category update and delete fail when category is marked as in use`() {
        val adminToken = login(email = "admin@beauty.local", password = "AdminPass123!")
        val categoryId = createCategory(adminToken, "Haircare")
        categoryUsageGuard.markInUse(categoryId)

        mockMvc
            .perform(
                put("/api/admin/categories/{categoryId}", categoryId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Haircare"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Category is in use: $categoryId"))

        mockMvc
            .perform(
                delete("/api/admin/categories/{categoryId}", categoryId)
                    .header("Authorization", "Bearer ${adminToken.accessToken}"),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Category is in use: $categoryId"))
    }

    @Test
    fun `category update and delete return not found for missing category`() {
        val adminToken = login(email = "admin@beauty.local", password = "AdminPass123!")

        mockMvc
            .perform(
                put("/api/admin/categories/{categoryId}", 999999)
                    .header("Authorization", "Bearer ${adminToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Haircare"}"""),
            ).andExpect(status().isNotFound)

        mockMvc
            .perform(
                delete("/api/admin/categories/{categoryId}", 999999)
                    .header("Authorization", "Bearer ${adminToken.accessToken}"),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `non admin cannot manage categories`() {
        signupBuyer(email = "buyer@beauty.local", password = "buyer-pass-123")
        val buyerToken = login(email = "buyer@beauty.local", password = "buyer-pass-123")

        mockMvc
            .perform(
                post("/api/admin/categories")
                    .header("Authorization", "Bearer ${buyerToken.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Skincare"}"""),
            ).andExpect(status().isForbidden)
    }

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

        val body: JsonNode = objectMapper.readTree(response.contentAsByteArray)
        return body["id"].asLong()
    }

    private fun signupBuyer(
        email: String,
        password: String,
        phoneNumber: String = "010-1234-5678",
    ) {
        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            mapOf(
                                "name" to "Buyer One",
                                "email" to email,
                                "password" to password,
                                "phoneNumber" to phoneNumber,
                                "postalCode" to "06236",
                            ),
                        ),
                    ),
            ).andExpect(status().isCreated)
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
                        .content(
                            objectMapper.writeValueAsBytes(
                                LoginRequest(
                                    email = email,
                                    password = password,
                                ),
                            ),
                        ),
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

@TestConfiguration
class CategoryUsageGuardTestConfiguration {
    @Bean
    @Primary
    fun testCategoryUsageGuard(): TestCategoryUsageGuard = TestCategoryUsageGuard()
}

class TestCategoryUsageGuard : CategoryUsageGuard {
    private val inUseCategoryIds = mutableSetOf<Long>()

    override fun isCategoryInUse(categoryId: Long): Boolean = inUseCategoryIds.contains(categoryId)

    fun markInUse(categoryId: Long) {
        inUseCategoryIds += categoryId
    }

    fun reset() {
        inUseCategoryIds.clear()
    }
}
