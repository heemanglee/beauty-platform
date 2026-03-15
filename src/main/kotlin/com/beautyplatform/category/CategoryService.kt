package com.beautyplatform.category

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val categoryUsageGuard: CategoryUsageGuard,
) {
    @Transactional
    fun createCategory(
        request: CreateCategoryRequest,
        adminUserId: Long,
    ): CategoryResponse {
        val normalizedName = request.name.trim()
        if (categoryRepository.existsByName(normalizedName)) {
            throw CategoryAlreadyExistsException(normalizedName)
        }

        val category =
            Category(
                name = normalizedName,
                createdByUserId = adminUserId,
                updatedByUserId = adminUserId,
            )

        return try {
            categoryRepository.save(category).toResponse()
        } catch (exception: DataIntegrityViolationException) {
            rethrowDuplicateConstraint(exception, normalizedName)
        }
    }

    @Transactional(readOnly = true)
    fun listCategories(): List<CategoryResponse> = categoryRepository.findAllByOrderByIdAsc().map(Category::toResponse)

    @Transactional
    fun updateCategory(
        categoryId: Long,
        request: UpdateCategoryRequest,
        adminUserId: Long,
    ): CategoryResponse {
        val category = categoryRepository.findByIdOrNull(categoryId) ?: throw CategoryNotFoundException(categoryId)
        if (categoryUsageGuard.isCategoryInUse(categoryId)) {
            throw CategoryInUseException(categoryId)
        }

        val normalizedName = request.name.trim()
        if (category.name != normalizedName && categoryRepository.existsByName(normalizedName)) {
            throw CategoryAlreadyExistsException(normalizedName)
        }

        category.name = normalizedName
        category.updatedByUserId = adminUserId

        return try {
            category.toResponse()
        } catch (exception: DataIntegrityViolationException) {
            rethrowDuplicateConstraint(exception, normalizedName)
        }
    }

    @Transactional
    fun deleteCategory(categoryId: Long) {
        val category = categoryRepository.findByIdOrNull(categoryId) ?: throw CategoryNotFoundException(categoryId)
        if (categoryUsageGuard.isCategoryInUse(categoryId)) {
            throw CategoryInUseException(categoryId)
        }

        categoryRepository.delete(category)
    }

    private fun rethrowDuplicateConstraint(
        exception: DataIntegrityViolationException,
        name: String,
    ): Nothing {
        val message = exception.mostSpecificCause.message.orEmpty()
        if (message.contains("uk_categories_name") || message.contains("name")) {
            throw CategoryAlreadyExistsException(name)
        }
        throw exception
    }
}

private fun Category.toResponse(): CategoryResponse =
    CategoryResponse(
        id = requireNotNull(id),
        name = name,
    )
