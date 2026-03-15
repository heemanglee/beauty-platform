package com.beautyplatform.product.repository

import com.beautyplatform.product.entity.Product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductRepository : JpaRepository<Product, Long> {
    @Query(
        """
        select distinct p
        from Product p
        left join fetch p.images
        where p.sellerId = :sellerId
        order by p.id asc
        """,
    )
    fun findAllWithImagesBySellerId(@Param("sellerId") sellerId: Long): List<Product>

    @Query(
        """
        select distinct p
        from Product p
        left join fetch p.images
        where p.id = :productId and p.sellerId = :sellerId
        """,
    )
    fun findOwnedWithImages(
        @Param("productId") productId: Long,
        @Param("sellerId") sellerId: Long,
    ): Product?

    @Query(
        """
        select distinct p
        from Product p
        left join fetch p.images
        order by p.id asc
        """,
    )
    fun findAllWithImages(): List<Product>

    @Query(
        """
        select distinct p
        from Product p
        left join fetch p.images
        where p.id = :productId
        """,
    )
    fun findWithImagesById(@Param("productId") productId: Long): Product?

    fun existsByCategoryId(categoryId: Long): Boolean
}
