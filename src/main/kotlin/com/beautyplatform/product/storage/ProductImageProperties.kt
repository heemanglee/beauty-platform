package com.beautyplatform.product.storage

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "app.product-images")
data class ProductImageProperties(
    @field:NotBlank
    val bucket: String,

    @field:NotBlank
    val region: String,

    @field:NotBlank
    val publicBaseUrl: String,

    @field:NotNull
    val uploadUrlTtl: Duration = Duration.ofMinutes(10),
)
