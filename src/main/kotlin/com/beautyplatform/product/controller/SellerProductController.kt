package com.beautyplatform.product.controller

import com.beautyplatform.common.security.requireUserId
import com.beautyplatform.product.dto.CreateProductRequest
import com.beautyplatform.product.dto.IssueProductImageUploadUrlRequest
import com.beautyplatform.product.dto.IssueProductImageUploadUrlResponse
import com.beautyplatform.product.dto.ProductDetailResponse
import com.beautyplatform.product.dto.ProductListResponse
import com.beautyplatform.product.dto.ProductStatusUpdateRequest
import com.beautyplatform.product.dto.UpdateProductRequest
import com.beautyplatform.product.service.ProductService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/seller/products")
class SellerProductController(
    private val productService: ProductService,
) {
    @PostMapping
    fun createProduct(
        authentication: JwtAuthenticationToken,
        @Valid @RequestBody request: CreateProductRequest,
    ): ResponseEntity<ProductDetailResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(authentication.requireUserId(), request))

    @GetMapping
    fun listProducts(
        authentication: JwtAuthenticationToken,
    ): List<ProductListResponse> = productService.listSellerProducts(authentication.requireUserId())

    @GetMapping("/{productId}")
    fun getProduct(
        authentication: JwtAuthenticationToken,
        @PathVariable productId: Long,
    ): ProductDetailResponse = productService.getSellerProduct(authentication.requireUserId(), productId)

    @PutMapping("/{productId}")
    fun updateProduct(
        authentication: JwtAuthenticationToken,
        @PathVariable productId: Long,
        @Valid @RequestBody request: UpdateProductRequest,
    ): ProductDetailResponse = productService.updateProduct(authentication.requireUserId(), productId, request)

    @PatchMapping("/{productId}/status")
    fun updateProductStatus(
        authentication: JwtAuthenticationToken,
        @PathVariable productId: Long,
        @Valid @RequestBody request: ProductStatusUpdateRequest,
    ): ProductDetailResponse = productService.updateProductStatusBySeller(authentication.requireUserId(), productId, request)

    @PostMapping("/{productId}/image-upload-url")
    fun issueImageUploadUrl(
        authentication: JwtAuthenticationToken,
        @PathVariable productId: Long,
        @Valid @RequestBody request: IssueProductImageUploadUrlRequest,
    ): IssueProductImageUploadUrlResponse = productService.issueUploadUrl(authentication.requireUserId(), productId, request)
}
