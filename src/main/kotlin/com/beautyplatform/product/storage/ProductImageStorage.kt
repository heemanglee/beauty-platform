package com.beautyplatform.product.storage

import com.beautyplatform.product.enums.ProductImageType

data class ProductImageUploadCommand(
    val type: ProductImageType,
    val filename: String,
    val contentType: String,
    val contentLength: Long,
)

data class IssuedProductImageUpload(
    val s3Key: String,
    val uploadUrl: String,
)

interface ProductImageStorage {
    fun issueUpload(
        productId: Long,
        command: ProductImageUploadCommand,
    ): IssuedProductImageUpload

    fun deleteObjects(s3Keys: Collection<String>)

    fun resolvePublicUrl(s3Key: String): String
}
