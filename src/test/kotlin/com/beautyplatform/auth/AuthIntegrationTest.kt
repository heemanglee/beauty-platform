package com.beautyplatform.auth

import com.beautyplatform.auth.dto.AuthTokenResponse
import com.beautyplatform.auth.dto.AuthenticatedUser
import com.beautyplatform.auth.dto.LoginRequest
import com.beautyplatform.auth.dto.SignupRequest
import com.beautyplatform.common.security.SecurityTestProbeConfiguration
import com.beautyplatform.product.ProductImageStorageTestConfiguration
import com.beautyplatform.user.entity.User
import com.beautyplatform.user.enums.UserRole
import com.beautyplatform.user.repository.UserRepository
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
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
class AuthIntegrationTest(
    @Autowired private val mockMvc: MockMvc,

    @Autowired private val objectMapper: ObjectMapper,

    @Autowired private val userRepository: UserRepository,

    @Autowired private val passwordEncoder: PasswordEncoder,

    @Autowired private val jwtDecoder: JwtDecoder,
) {
    @BeforeEach
    fun setUp() {
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
    fun `signup creates buyer with normalized email and hashed password`() {
        val request =
            SignupRequest(
                name = "Buyer One",
                email = "Buyer.One@Example.COM",
                password = "buyer-pass-123",
                phoneNumber = "010-1234-5678",
                postalCode = "06236",
            )

        val response =
            mockMvc
                .perform(
                    post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)),
                ).andExpect(status().isCreated)
                .andReturn()
                .response

        val body = objectMapper.readValue(response.contentAsByteArray, AuthenticatedUser::class.java)
        assertThat(body.email).isEqualTo("buyer.one@example.com")
        assertThat(body.role).isEqualTo(UserRole.BUYER)

        val savedUser = userRepository.findByEmail("buyer.one@example.com")
        assertThat(savedUser).isNotNull
        assertThat(savedUser!!.postalCode).isEqualTo("06236")
        assertThat(savedUser.phoneNumber).isEqualTo("010-1234-5678")
        assertThat(savedUser.passwordHash).isNotEqualTo("buyer-pass-123")
        assertThat(passwordEncoder.matches("buyer-pass-123", savedUser.passwordHash)).isTrue()
    }

    @Test
    fun `signup rejects duplicate email when admin already owns it`() {
        val request =
            SignupRequest(
                name = "Buyer One",
                email = "ADMIN@BEAUTY.LOCAL",
                password = "buyer-pass-123",
                phoneNumber = "010-1234-5678",
                postalCode = "06236",
            )

        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Email already exists: admin@beauty.local"))
    }

    @Test
    fun `signup rejects duplicate email when seller already owns it`() {
        userRepository.save(
            User(
                role = UserRole.SELLER,
                name = "Seller One",
                email = "seller@beauty.local",
                passwordHash = passwordEncoder.encode("seller-pass-123"),
                phoneNumber = "010-2222-3333",
            ),
        )

        val request =
            SignupRequest(
                name = "Buyer One",
                email = "seller@beauty.local",
                password = "buyer-pass-123",
                phoneNumber = "010-1234-5678",
                postalCode = "06236",
            )

        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `signup rejects duplicate email when buyer already owns it`() {
        val request =
            SignupRequest(
                name = "Buyer One",
                email = "buyer@beauty.local",
                password = "buyer-pass-123",
                phoneNumber = "010-1234-5678",
                postalCode = "06236",
            )

        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `signup rejects duplicate phone number`() {
        signupBuyer(email = "buyer1@beauty.local", password = "buyer-pass-123", phoneNumber = "010-1234-5678")

        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            SignupRequest(
                                name = "Buyer Two",
                                email = "buyer2@beauty.local",
                                password = "buyer-pass-456",
                                phoneNumber = "010-1234-5678",
                                postalCode = "04524",
                            ),
                        ),
                    ),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Phone number already exists: 010-1234-5678"))
    }

    @Test
    fun `buyer login returns jwt with configured issuer`() {
        signupBuyer(email = "buyer@beauty.local", password = "buyer-pass-123")

        val loginResponse = login(email = "buyer@beauty.local", password = "buyer-pass-123")

        assertThat(loginResponse.tokenType).isEqualTo("Bearer")
        assertThat(loginResponse.expiresIn).isEqualTo(3600)
        assertThat(loginResponse.user.role).isEqualTo(UserRole.BUYER)

        val jwt = jwtDecoder.decode(loginResponse.accessToken)
        assertThat(jwt.issuer.toString()).isEqualTo("http://localhost:8080")
        assertThat(jwt.getClaimAsString("role")).isEqualTo(UserRole.BUYER.name)
        assertThat(jwt.getClaimAsString("email")).isEqualTo("buyer@beauty.local")
    }

    @Test
    fun `login fails when password is incorrect`() {
        signupBuyer(email = "buyer@beauty.local", password = "buyer-pass-123")

        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            LoginRequest(
                                email = "buyer@beauty.local",
                                password = "wrong-password",
                            ),
                        ),
                    ),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `seeded admin can login`() {
        val loginResponse = login(email = "admin@beauty.local", password = "AdminPass123!")

        assertThat(loginResponse.user.role).isEqualTo(UserRole.ADMIN)
        assertThat(loginResponse.user.email).isEqualTo("admin@beauty.local")
    }

    @Test
    fun `fixture seller can login`() {
        userRepository.save(
            User(
                role = UserRole.SELLER,
                name = "Seller One",
                email = "seller@beauty.local",
                passwordHash = passwordEncoder.encode("seller-pass-123"),
                phoneNumber = "010-2222-3333",
            ),
        )

        val loginResponse = login(email = "seller@beauty.local", password = "seller-pass-123")

        assertThat(loginResponse.user.role).isEqualTo(UserRole.SELLER)
    }

    @Test
    fun `public path is accessible without authentication`() {
        mockMvc
            .perform(get("/api/public/probe"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scope").value("public"))
    }

    @Test
    fun `protected path rejects unauthenticated request`() {
        mockMvc
            .perform(get("/api/buyer/probe"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected path rejects authenticated user with wrong role`() {
        signupBuyer(email = "buyer@beauty.local", password = "buyer-pass-123")
        val buyerToken = login(email = "buyer@beauty.local", password = "buyer-pass-123").accessToken

        mockMvc
            .perform(
                get("/api/admin/probe")
                    .header("Authorization", "Bearer $buyerToken"),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `protected path allows matching role`() {
        val adminToken = login(email = "admin@beauty.local", password = "AdminPass123!").accessToken

        mockMvc
            .perform(
                get("/api/admin/probe")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.scope").value("admin"))
    }

    private fun signupBuyer(
        email: String,
        password: String,
        phoneNumber: String = "010-1234-5678",
    ) {
        val request =
            SignupRequest(
                name = "Buyer One",
                email = email,
                password = password,
                phoneNumber = phoneNumber,
                postalCode = "06236",
            )

        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
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
