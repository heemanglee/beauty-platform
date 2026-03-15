package com.beautyplatform.common.api

import com.beautyplatform.auth.exception.EmailAlreadyExistsException
import com.beautyplatform.auth.exception.InvalidCredentialsException
import com.beautyplatform.auth.exception.PhoneNumberAlreadyExistsException
import com.beautyplatform.category.CategoryAlreadyExistsException
import com.beautyplatform.category.CategoryInUseException
import com.beautyplatform.category.CategoryNotFoundException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val validationErrors =
            exception.bindingResult.fieldErrors.map {
                ValidationError(
                    field = it.field,
                    message = it.defaultMessage ?: "Invalid value",
                )
            }
        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = "Validation failed",
                path = request.requestURI,
                validationErrors = validationErrors,
            ),
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableRequest(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val validationErrors = extractJsonValidationErrors(exception)
        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = if (validationErrors.isEmpty()) "Malformed request body" else "Validation failed",
                path = request.requestURI,
                validationErrors = validationErrors,
            ),
        )
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleDuplicateEmail(
        exception: EmailAlreadyExistsException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = HttpStatus.CONFLICT.reasonPhrase,
                message = exception.message ?: "Email already exists",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(PhoneNumberAlreadyExistsException::class)
    fun handleDuplicatePhoneNumber(
        exception: PhoneNumberAlreadyExistsException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = HttpStatus.CONFLICT.reasonPhrase,
                message = exception.message ?: "Phone number already exists",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(
        exception: InvalidCredentialsException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse(
                status = HttpStatus.UNAUTHORIZED.value(),
                error = HttpStatus.UNAUTHORIZED.reasonPhrase,
                message = exception.message ?: "Invalid email or password",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(CategoryAlreadyExistsException::class)
    fun handleDuplicateCategory(
        exception: CategoryAlreadyExistsException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = HttpStatus.CONFLICT.reasonPhrase,
                message = exception.message ?: "Category already exists",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(CategoryInUseException::class)
    fun handleCategoryInUse(
        exception: CategoryInUseException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = HttpStatus.CONFLICT.reasonPhrase,
                message = exception.message ?: "Category is in use",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(CategoryNotFoundException::class)
    fun handleCategoryNotFound(
        exception: CategoryNotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = HttpStatus.NOT_FOUND.reasonPhrase,
                message = exception.message ?: "Category not found",
                path = request.requestURI,
            ),
        )

    private fun extractJsonValidationErrors(exception: HttpMessageNotReadableException): List<ValidationError> {
        val unknownField = findCause<UnrecognizedPropertyException>(exception)
        if (unknownField != null) {
            return listOf(
                ValidationError(
                    field = unknownField.propertyName,
                    message = "Unknown field",
                ),
            )
        }

        val missingField = findCause<MissingKotlinParameterException>(exception)
        if (missingField != null) {
            return listOf(
                ValidationError(
                    field = missingField.parameter.name ?: missingField.path.lastOrNull()?.fieldName ?: "requestBody",
                    message = "Required field is missing",
                ),
            )
        }

        val invalidFormat = findCause<InvalidFormatException>(exception)
        if (invalidFormat != null) {
            return listOf(
                ValidationError(
                    field = invalidFormat.path.lastOrNull()?.fieldName ?: "requestBody",
                    message = "Invalid value",
                ),
            )
        }

        val mismatchedInput = findCause<MismatchedInputException>(exception)
        if (mismatchedInput != null && mismatchedInput.path.isNotEmpty()) {
            return listOf(
                ValidationError(
                    field = mismatchedInput.path.last().fieldName ?: "requestBody",
                    message = "Invalid value",
                ),
            )
        }

        return emptyList()
    }

    private inline fun <reified T : Throwable> findCause(exception: Throwable): T? {
        var current: Throwable? = exception
        while (current != null) {
            if (current is T) {
                return current
            }
            current = current.cause
        }
        return null
    }
}
