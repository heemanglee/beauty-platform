package com.beautyplatform.category.repository

import com.beautyplatform.category.entity.Category
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Long> {
    fun existsByName(name: String): Boolean

    fun findAllByOrderByIdAsc(): List<Category>
}
