package com.beautyplatform.category

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Long> {
    fun existsByName(name: String): Boolean

    fun findAllByOrderByIdAsc(): List<Category>
}
