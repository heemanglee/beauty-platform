package com.beautyplatform.product.entity

import com.beautyplatform.product.enums.ProductStatus

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "seller_id", nullable = false)
    var sellerId: Long,

    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    @Column(nullable = false, length = 150)
    var name: String,

    @Column(nullable = false)
    var price: Long,

    @Column(name = "stock_quantity", nullable = false)
    var stockQuantity: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ProductStatus = ProductStatus.PENDING,

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val images: MutableList<ProductImage> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    fun replaceImages(newImages: List<ProductImage>) {
        images.clear()
        newImages.forEach(::addImage)
    }

    private fun addImage(image: ProductImage) {
        image.product = this
        images += image
    }
}
