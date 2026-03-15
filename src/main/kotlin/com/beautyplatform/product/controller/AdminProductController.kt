package com.beautyplatform.product.controller

import com.beautyplatform.product.dto.ProductDetailResponse
import com.beautyplatform.product.dto.ProductListResponse
import com.beautyplatform.product.dto.ProductStatusUpdateRequest
import com.beautyplatform.product.service.ProductService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/products")
class AdminProductController(
    private val productService: ProductService,
) {
    @GetMapping
    fun listProducts(): List<ProductListResponse> = productService.listAdminProducts()

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long,
    ): ProductDetailResponse = productService.getAdminProduct(productId)

    @PatchMapping("/{productId}/status")
    fun updateProductStatus(
        @PathVariable productId: Long,
        @Valid @RequestBody request: ProductStatusUpdateRequest,
    ): ProductDetailResponse = productService.updateProductStatusByAdmin(productId, request)
}
