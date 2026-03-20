package com.beautyplatform.seller.controller

import com.beautyplatform.seller.dto.AdminCreateSellerRequest
import com.beautyplatform.seller.dto.AdminSellerResponse
import com.beautyplatform.seller.service.SellerService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/sellers")
class SellerAdminController(
    private val sellerService: SellerService,
) {
    @PostMapping
    fun createSeller(
        @Valid @RequestBody request: AdminCreateSellerRequest,
    ): ResponseEntity<AdminSellerResponse> = ResponseEntity.status(HttpStatus.CREATED).body(sellerService.createSeller(request))
}
