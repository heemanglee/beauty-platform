package com.beautyplatform.auth.controller

import com.beautyplatform.auth.dto.AuthTokenResponse
import com.beautyplatform.auth.dto.AuthenticatedUser
import com.beautyplatform.auth.dto.LoginRequest
import com.beautyplatform.auth.dto.SignupRequest
import com.beautyplatform.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
    ): ResponseEntity<AuthenticatedUser> = ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request))

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): AuthTokenResponse = authService.login(request)
}
