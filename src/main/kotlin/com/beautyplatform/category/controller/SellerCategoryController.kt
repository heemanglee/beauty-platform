package com.beautyplatform.category.controller

import com.beautyplatform.category.dto.CategoryResponse
import com.beautyplatform.category.service.CategoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/seller/categories")
class SellerCategoryController(
    private val categoryService: CategoryService,
) {
    @GetMapping
    fun listCategories(): List<CategoryResponse> = categoryService.listCategories()
}
