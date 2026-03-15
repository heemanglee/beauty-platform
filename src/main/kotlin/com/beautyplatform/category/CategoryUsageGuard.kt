package com.beautyplatform.category

import org.springframework.stereotype.Component

interface CategoryUsageGuard {
    fun isCategoryInUse(categoryId: Long): Boolean
}

@Component
class NoopCategoryUsageGuard : CategoryUsageGuard {
    override fun isCategoryInUse(categoryId: Long): Boolean = false
}
