package com.beautyplatform.category.support

import com.beautyplatform.product.repository.ProductRepository
import org.springframework.stereotype.Component

interface CategoryUsageGuard {
    fun isCategoryInUse(categoryId: Long): Boolean
}

@Component
class ProductCategoryUsageGuard(
    private val productRepository: ProductRepository,
) : CategoryUsageGuard {
    override fun isCategoryInUse(categoryId: Long): Boolean = productRepository.existsByCategoryId(categoryId)
}
