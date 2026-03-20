package com.beautyplatform.auth.dto

import com.beautyplatform.user.enums.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 255)
    val password: String,

    @field:NotBlank
    @field:Size(max = 30)
    val phoneNumber: String,

    @field:NotBlank
    @field:Size(max = 20)
    val postalCode: String,
)

data class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(max = 255)
    val password: String,
)

data class AuthenticatedUser(
    val id: Long,
    val name: String,
    val email: String,
    val role: UserRole,
)

data class AuthTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: AuthenticatedUser,
)
