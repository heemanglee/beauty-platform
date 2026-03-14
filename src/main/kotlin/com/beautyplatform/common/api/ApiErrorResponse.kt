package com.beautyplatform.common.api

data class ApiErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val validationErrors: List<ValidationError> = emptyList(),
)

data class ValidationError(
    val field: String,
    val message: String,
)
