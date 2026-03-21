package com.beautyplatform.product.controller

import com.beautyplatform.product.dto.BuyerProductDetailResponse
import com.beautyplatform.product.dto.BuyerProductListResponse
import com.beautyplatform.product.enums.ProductSortType
import com.beautyplatform.product.service.ProductService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public/products")
class PublicProductController(
    private val productService: ProductService,
) {
    @GetMapping
    fun listProducts(
        @RequestParam(required = false) sellerId: Long?,
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "LATEST") sort: ProductSortType,
    ): List<BuyerProductListResponse> = productService.listPublicProducts(sellerId, categoryId, keyword, sort)

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: Long,
    ): BuyerProductDetailResponse = productService.getPublicProduct(productId)
}
