package com.beautyplatform.category.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateCategoryRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
)

data class UpdateCategoryRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
)

data class CategoryResponse(
    val id: Long,
    val name: String,
)
