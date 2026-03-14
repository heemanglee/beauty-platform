package com.beautyplatform.auth.exception

class EmailAlreadyExistsException(
    email: String,
) : RuntimeException("Email already exists: $email")

class PhoneNumberAlreadyExistsException(
    phoneNumber: String,
) : RuntimeException("Phone number already exists: $phoneNumber")

class InvalidCredentialsException : RuntimeException("Invalid email or password")
