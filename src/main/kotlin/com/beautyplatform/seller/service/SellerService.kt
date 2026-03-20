package com.beautyplatform.seller.service

import com.beautyplatform.auth.exception.EmailAlreadyExistsException
import com.beautyplatform.auth.exception.PhoneNumberAlreadyExistsException
import com.beautyplatform.seller.dto.AdminCreateSellerRequest
import com.beautyplatform.seller.dto.AdminSellerResponse
import com.beautyplatform.user.entity.User
import com.beautyplatform.user.enums.UserRole
import com.beautyplatform.user.repository.UserRepository
import com.beautyplatform.user.support.EmailNormalizer
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SellerService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun createSeller(request: AdminCreateSellerRequest): AdminSellerResponse {
        val normalizedEmail = EmailNormalizer.normalize(request.email)
        val normalizedPhoneNumber = request.phoneNumber.trim()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw EmailAlreadyExistsException(normalizedEmail)
        }
        if (userRepository.existsByPhoneNumber(normalizedPhoneNumber)) {
            throw PhoneNumberAlreadyExistsException(normalizedPhoneNumber)
        }

        val seller =
            User(
                role = UserRole.SELLER,
                name = request.name.trim(),
                email = normalizedEmail,
                passwordHash = passwordEncoder.encode(request.password),
                phoneNumber = normalizedPhoneNumber,
            )

        return try {
            userRepository.save(seller).toAdminSellerResponse()
        } catch (exception: DataIntegrityViolationException) {
            rethrowDuplicateConstraint(exception, normalizedEmail, normalizedPhoneNumber)
        }
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

private fun User.toAdminSellerResponse(): AdminSellerResponse =
    AdminSellerResponse(
        id = requireNotNull(id),
        name = name,
        email = email,
        phoneNumber = requireNotNull(phoneNumber),
        role = role,
    )
