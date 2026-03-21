package com.beautyplatform.product.dto

import com.beautyplatform.product.enums.ProductImageType
import com.beautyplatform.product.enums.ProductStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class CreateProductRequest(
    @field:NotBlank
    @field:Size(max = 150)
    val name: String,

    @field:Positive
    val price: Long,

    @field:PositiveOrZero
    val stockQuantity: Int,

    @field:Positive
    val categoryId: Long,

    @field:Valid
    val images: ProductImageGroupRequest,
)

data class UpdateProductRequest(
    @field:NotBlank
    @field:Size(max = 150)
    val name: String,

    @field:Positive
    val price: Long,

    @field:PositiveOrZero
    val stockQuantity: Int,

    @field:Positive
    val categoryId: Long,

    @field:Valid
    val images: ProductImageGroupRequest,
)

data class ProductImageGroupRequest(
    @field:Valid
    @field:Size(max = 5)
    val thumbnailImages: List<ProductImageItemRequest> = emptyList(),

    @field:Valid
    @field:Size(max = 10)
    val mainImages: List<ProductImageItemRequest> = emptyList(),

    @field:Valid
    @field:Size(max = 20)
    val descriptionImages: List<ProductImageItemRequest> = emptyList(),
)

data class ProductImageItemRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val s3Key: String,

    @field:Positive
    val sortOrder: Int,
)

data class ProductStatusUpdateRequest(
    @field:NotNull
    val status: ProductStatus,
)

data class IssueProductImageUploadUrlRequest(
    @field:NotNull
    val type: ProductImageType,

    @field:NotBlank
    @field:Size(max = 255)
    val filename: String,

    @field:NotBlank
    @field:Size(max = 100)
    val contentType: String,

    @field:Positive
    @field:Max(10_485_760)
    val contentLength: Long,
)

data class IssueProductImageUploadUrlResponse(
    val s3Key: String,
    val uploadUrl: String,
)

data class ProductListResponse(
    val id: Long,
    val sellerId: Long,
    val categoryId: Long,
    val name: String,
    val price: Long,
    val stockQuantity: Int,
    val status: ProductStatus,
    val thumbnailImageUrl: String?,
)

data class ProductDetailResponse(
    val id: Long,
    val sellerId: Long,
    val categoryId: Long,
    val name: String,
    val price: Long,
    val stockQuantity: Int,
    val status: ProductStatus,
    val images: ProductImageGroupResponse,
)

data class ProductImageGroupResponse(
    val thumbnailImages: List<ProductImageResponse>,
    val mainImages: List<ProductImageResponse>,
    val descriptionImages: List<ProductImageResponse>,
)

data class ProductImageResponse(
    val type: ProductImageType,
    val sortOrder: Int,
    val url: String,
)

data class BuyerProductListResponse(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val price: Long,
    val sellerName: String,
    val status: ProductStatus,
    val purchasable: Boolean,
    val thumbnailImageUrl: String?,
)

data class BuyerProductDetailResponse(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val price: Long,
    val sellerName: String,
    val status: ProductStatus,
    val purchasable: Boolean,
    val images: ProductImageGroupResponse,
)
