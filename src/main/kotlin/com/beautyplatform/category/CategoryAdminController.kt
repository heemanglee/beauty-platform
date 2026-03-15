package com.beautyplatform.category

import com.beautyplatform.common.security.requireUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/categories")
class CategoryAdminController(
    private val categoryService: CategoryService,
) {
    @PostMapping
    fun createCategory(
        authentication: JwtAuthenticationToken,
        @Valid @RequestBody request: CreateCategoryRequest,
    ): ResponseEntity<CategoryResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request, authentication.requireUserId()))

    @GetMapping
    fun listCategories(): List<CategoryResponse> = categoryService.listCategories()

    @PutMapping("/{categoryId}")
    fun updateCategory(
        authentication: JwtAuthenticationToken,
        @PathVariable categoryId: Long,
        @Valid @RequestBody request: UpdateCategoryRequest,
    ): CategoryResponse = categoryService.updateCategory(categoryId, request, authentication.requireUserId())

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(
        @PathVariable categoryId: Long,
    ) {
        categoryService.deleteCategory(categoryId)
    }
}
