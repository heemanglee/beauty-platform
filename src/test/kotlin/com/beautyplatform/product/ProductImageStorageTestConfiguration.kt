package com.beautyplatform.product

import com.beautyplatform.product.exception.ProductImageStorageException
import com.beautyplatform.product.storage.IssuedProductImageUpload
import com.beautyplatform.product.storage.ProductImageStorage
import com.beautyplatform.product.storage.ProductImageUploadCommand
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.atomic.AtomicLong

@TestConfiguration
class ProductImageStorageTestConfiguration {
    @Bean
    @Primary
    fun testProductImageStorage(): TestProductImageStorage = TestProductImageStorage()
}

class TestProductImageStorage : ProductImageStorage {
    private val sequence = AtomicLong(1)
    private val failOnDeleteKeys = mutableSetOf<String>()

    override fun issueUpload(
        productId: Long,
        command: ProductImageUploadCommand,
    ): IssuedProductImageUpload {
        val extension = command.filename.substringAfterLast('.', missingDelimiterValue = "jpg")
        val key = "product_images/$productId/test-${sequence.getAndIncrement()}.$extension"
        return IssuedProductImageUpload(
            s3Key = key,
            uploadUrl = "https://uploads.test.example.com/$key",
        )
    }

    override fun deleteObjects(s3Keys: Collection<String>) {
        s3Keys.forEach { key ->
            if (failOnDeleteKeys.remove(key)) {
                throw ProductImageStorageException("Failed to delete product image: $key")
            }
        }
    }

    override fun resolvePublicUrl(s3Key: String): String = "https://cdn.test.example.com/$s3Key"

    fun failOnDelete(key: String) {
        failOnDeleteKeys += key
    }

    fun reset() {
        failOnDeleteKeys.clear()
    }
}
