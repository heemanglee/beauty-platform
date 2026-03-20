package com.beautyplatform.seller.dto

import com.beautyplatform.user.enums.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AdminCreateSellerRequest(
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
)

data class AdminSellerResponse(
    val id: Long,
    val name: String,
    val email: String,
    val phoneNumber: String,
    val role: UserRole,
)
