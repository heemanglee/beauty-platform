package com.beautyplatform.product.storage

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
class ProductImageStorageConfig(
    private val properties: ProductImageProperties,
) {
    @Bean(destroyMethod = "close")
    fun s3Client(): S3Client = S3Client.builder().region(Region.of(properties.region)).build()

    @Bean(destroyMethod = "close")
    fun s3Presigner(): S3Presigner = S3Presigner.builder().region(Region.of(properties.region)).build()

    @Bean
    fun productImageStorage(
        s3Client: S3Client,
        s3Presigner: S3Presigner,
    ): ProductImageStorage = S3ProductImageStorage(properties, s3Client, s3Presigner)
}
