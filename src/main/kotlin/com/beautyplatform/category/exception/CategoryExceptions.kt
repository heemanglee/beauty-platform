package com.beautyplatform.category.exception

class CategoryAlreadyExistsException(
    name: String,
) : RuntimeException("Category already exists: $name")

class CategoryNotFoundException(
    categoryId: Long,
) : RuntimeException("Category not found: $categoryId")

class CategoryInUseException(
    categoryId: Long,
) : RuntimeException("Category is in use: $categoryId")
