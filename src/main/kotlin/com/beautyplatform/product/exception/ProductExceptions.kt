package com.beautyplatform.product.exception

class ProductNotFoundException(
    productId: Long,
) : RuntimeException("Product not found: $productId")

class ProductStatusTransitionException(
    message: String,
) : RuntimeException(message)

class ProductImageValidationException(
    message: String,
) : RuntimeException(message)

class ProductImageStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
