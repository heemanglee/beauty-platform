package com.beautyplatform.common.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.nio.charset.StandardCharsets
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "app.auth.jwt")
data class JwtProperties(
    @field:NotBlank
    val issuer: String,

    @field:NotBlank
    val secret: String,
    val accessTokenTtl: Duration = Duration.ofMinutes(60),
) {
    init {
        require(secret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "JWT secret must be at least 32 bytes for HS256"
        }
    }
}
