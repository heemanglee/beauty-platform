package com.beautyplatform.auth.service

import com.beautyplatform.auth.dto.AuthTokenResponse
import com.beautyplatform.auth.dto.AuthenticatedUser
import com.beautyplatform.auth.dto.LoginRequest
import com.beautyplatform.auth.dto.SignupRequest
import com.beautyplatform.auth.exception.EmailAlreadyExistsException
import com.beautyplatform.auth.exception.InvalidCredentialsException
import com.beautyplatform.auth.exception.PhoneNumberAlreadyExistsException
import com.beautyplatform.common.security.JwtTokenService
import com.beautyplatform.user.EmailNormalizer
import com.beautyplatform.user.User
import com.beautyplatform.user.UserRepository
import com.beautyplatform.user.UserRole
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
) {
    @Transactional
    fun signup(request: SignupRequest): AuthenticatedUser {
        val normalizedEmail = EmailNormalizer.normalize(request.email)
        val normalizedPhoneNumber = request.phoneNumber.trim()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw EmailAlreadyExistsException(normalizedEmail)
        }
        if (userRepository.existsByPhoneNumber(normalizedPhoneNumber)) {
            throw PhoneNumberAlreadyExistsException(normalizedPhoneNumber)
        }

        val user =
            User(
                role = UserRole.BUYER,
                name = request.name.trim(),
                email = normalizedEmail,
                passwordHash = passwordEncoder.encode(request.password),
                phoneNumber = normalizedPhoneNumber,
                postalCode = request.postalCode.trim(),
            )

        return try {
            userRepository.save(user).toAuthenticatedUser()
        } catch (exception: DataIntegrityViolationException) {
            rethrowDuplicateConstraint(exception, normalizedEmail, normalizedPhoneNumber)
        }
    }

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthTokenResponse {
        val normalizedEmail = EmailNormalizer.normalize(request.email)
        val user = userRepository.findByEmail(normalizedEmail) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val token = jwtTokenService.issueToken(user)
        return AuthTokenResponse(
            accessToken = token.accessToken,
            expiresIn = token.expiresIn,
            user = user.toAuthenticatedUser(),
        )
    }

    private fun rethrowDuplicateConstraint(
        exception: DataIntegrityViolationException,
        email: String,
        phoneNumber: String,
    ): Nothing {
        val message = exception.mostSpecificCause.message.orEmpty()
        if (message.contains("uk_users_email") || message.contains("email")) {
            throw EmailAlreadyExistsException(email)
        }
        if (message.contains("uk_users_phone_number") || message.contains("phone_number")) {
            throw PhoneNumberAlreadyExistsException(phoneNumber)
        }
        throw exception
    }
}

private fun User.toAuthenticatedUser(): AuthenticatedUser =
    AuthenticatedUser(
        id = requireNotNull(id),
        name = name,
        email = email,
        role = role,
    )
