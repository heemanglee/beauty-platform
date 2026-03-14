package com.beautyplatform.common.security

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@TestConfiguration
class SecurityTestProbeConfiguration {
    @Bean
    fun securityProbeController(): SecurityProbeController = SecurityProbeController()
}

@RestController
class SecurityProbeController {
    @GetMapping("/api/public/probe")
    fun publicProbe(): Map<String, String> = mapOf("scope" to "public")

    @GetMapping("/api/buyer/probe")
    fun buyerProbe(): Map<String, String> = mapOf("scope" to "buyer")

    @GetMapping("/api/seller/probe")
    fun sellerProbe(): Map<String, String> = mapOf("scope" to "seller")

    @GetMapping("/api/admin/probe")
    fun adminProbe(): Map<String, String> = mapOf("scope" to "admin")
}
