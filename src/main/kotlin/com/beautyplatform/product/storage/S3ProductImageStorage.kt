package com.beautyplatform.product.storage

import com.beautyplatform.product.exception.ProductImageStorageException
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class S3ProductImageStorage(
    private val properties: ProductImageProperties,
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
) : ProductImageStorage {
    override fun issueUpload(
        productId: Long,
        command: ProductImageUploadCommand,
    ): IssuedProductImageUpload {
        val extension = command.filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val s3Key = "product_images/$productId/${UUID.randomUUID()}.$extension"
        val putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(s3Key)
                .contentType(command.contentType)
                .build()

        return try {
            val presignedRequest =
                s3Presigner.presignPutObject(
                    PutObjectPresignRequest
                        .builder()
                        .signatureDuration(properties.uploadUrlTtl)
                        .putObjectRequest(putObjectRequest)
                        .build(),
                )
            IssuedProductImageUpload(
                s3Key = s3Key,
                uploadUrl = presignedRequest.url().toString(),
            )
        } catch (exception: SdkException) {
            throw ProductImageStorageException("Failed to issue product image upload URL", exception)
        }
    }

    override fun deleteObjects(s3Keys: Collection<String>) {
        s3Keys.forEach { s3Key ->
            try {
                s3Client.deleteObject(
                    DeleteObjectRequest
                        .builder()
                        .bucket(properties.bucket)
                        .key(s3Key)
                        .build(),
                )
            } catch (exception: S3Exception) {
                throw ProductImageStorageException("Failed to delete product image: $s3Key", exception)
            } catch (exception: SdkException) {
                throw ProductImageStorageException("Failed to delete product image: $s3Key", exception)
            }
        }
    }

    override fun resolvePublicUrl(s3Key: String): String {
        val baseUrl = properties.publicBaseUrl.trimEnd('/')
        val encodedKey = s3Key.split("/").joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        return "$baseUrl/$encodedKey"
    }
}
