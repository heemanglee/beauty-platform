package com.beautyplatform.product.service

import com.beautyplatform.category.CategoryNotFoundException
import com.beautyplatform.category.CategoryRepository
import com.beautyplatform.product.dto.CreateProductRequest
import com.beautyplatform.product.dto.IssueProductImageUploadUrlRequest
import com.beautyplatform.product.dto.IssueProductImageUploadUrlResponse
import com.beautyplatform.product.dto.ProductDetailResponse
import com.beautyplatform.product.dto.ProductImageGroupRequest
import com.beautyplatform.product.dto.ProductImageGroupResponse
import com.beautyplatform.product.dto.ProductImageItemRequest
import com.beautyplatform.product.dto.ProductImageResponse
import com.beautyplatform.product.dto.ProductListResponse
import com.beautyplatform.product.dto.ProductStatusUpdateRequest
import com.beautyplatform.product.dto.UpdateProductRequest
import com.beautyplatform.product.entity.Product
import com.beautyplatform.product.entity.ProductImage
import com.beautyplatform.product.enums.ProductImageType
import com.beautyplatform.product.enums.ProductStatus
import com.beautyplatform.product.exception.ProductImageStorageException
import com.beautyplatform.product.exception.ProductImageValidationException
import com.beautyplatform.product.exception.ProductNotFoundException
import com.beautyplatform.product.exception.ProductStatusTransitionException
import com.beautyplatform.product.repository.ProductRepository
import com.beautyplatform.product.storage.IssuedProductImageUpload
import com.beautyplatform.product.storage.ProductImageStorage
import com.beautyplatform.product.storage.ProductImageUploadCommand
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val productImageStorage: ProductImageStorage,
) {
    @Transactional
    fun createProduct(
        sellerId: Long,
        request: CreateProductRequest,
    ): ProductDetailResponse {
        requireExistingCategory(request.categoryId)

        val product =
            productRepository.save(
                Product(
                    sellerId = sellerId,
                    categoryId = request.categoryId,
                    name = request.name.trim(),
                    price = request.price,
                    stockQuantity = request.stockQuantity,
                    status = ProductStatus.PENDING,
                ),
            )

        val productId = requireNotNull(product.id)
        val imageSpecs = validateImages(productId, request.images)
        product.replaceImages(imageSpecs.map(ProductImageSpec::toEntity))

        return try {
            productRepository.flush()
            product.toDetailResponse(productImageStorage)
        } catch (exception: DataIntegrityViolationException) {
            throw ProductImageValidationException("Invalid product image configuration")
        }
    }

    @Transactional(readOnly = true)
    fun listSellerProducts(sellerId: Long): List<ProductListResponse> =
        productRepository.findAllWithImagesBySellerId(sellerId).map { it.toListResponse(productImageStorage) }

    @Transactional(readOnly = true)
    fun getSellerProduct(
        sellerId: Long,
        productId: Long,
    ): ProductDetailResponse = findOwnedProduct(productId, sellerId).toDetailResponse(productImageStorage)

    @Transactional
    fun updateProduct(
        sellerId: Long,
        productId: Long,
        request: UpdateProductRequest,
    ): ProductDetailResponse {
        requireExistingCategory(request.categoryId)
        val product = findOwnedProduct(productId, sellerId)
        val oldS3Keys = product.images.map(ProductImage::s3Key).toSet()
        val imageSpecs = validateImages(productId, request.images)

        product.name = request.name.trim()
        product.price = request.price
        product.stockQuantity = request.stockQuantity
        product.categoryId = request.categoryId

        if (product.status == ProductStatus.ON_SALE && request.stockQuantity > 0) {
            ensureCanBeOnSale(product, imageSpecs)
        }

        try {
            product.replaceImages(emptyList())
            productRepository.flush()
            product.replaceImages(imageSpecs.map(ProductImageSpec::toEntity))
        } catch (exception: DataIntegrityViolationException) {
            throw ProductImageValidationException("Invalid product image configuration")
        }
        applyInventoryStatus(product)

        val newS3Keys = imageSpecs.map(ProductImageSpec::s3Key).toSet()
        val removedS3Keys = oldS3Keys - newS3Keys
        productImageStorage.deleteObjects(removedS3Keys)

        return try {
            productRepository.flush()
            product.toDetailResponse(productImageStorage)
        } catch (exception: DataIntegrityViolationException) {
            throw ProductImageValidationException("Invalid product image configuration")
        }
    }

    @Transactional(readOnly = true)
    fun listAdminProducts(): List<ProductListResponse> = productRepository.findAllWithImages().map { it.toListResponse(productImageStorage) }

    @Transactional(readOnly = true)
    fun getAdminProduct(productId: Long): ProductDetailResponse =
        productRepository.findWithImagesById(productId)?.toDetailResponse(productImageStorage) ?: throw ProductNotFoundException(productId)

    @Transactional
    fun updateProductStatusBySeller(
        sellerId: Long,
        productId: Long,
        request: ProductStatusUpdateRequest,
    ): ProductDetailResponse {
        val product = findOwnedProduct(productId, sellerId)
        if (request.status != ProductStatus.ON_SALE) {
            throw ProductStatusTransitionException("Seller can only change product status to ON_SALE")
        }
        if (product.status == ProductStatus.BANNED) {
            throw ProductStatusTransitionException("BANNED product cannot be moved to ON_SALE by seller")
        }
        ensureCanBeOnSale(product, product.images.map(ProductImage::toSpec))
        product.status = ProductStatus.ON_SALE
        return product.toDetailResponse(productImageStorage)
    }

    @Transactional
    fun updateProductStatusByAdmin(
        productId: Long,
        request: ProductStatusUpdateRequest,
    ): ProductDetailResponse {
        val product = productRepository.findWithImagesById(productId) ?: throw ProductNotFoundException(productId)
        if (request.status != ProductStatus.BANNED) {
            throw ProductStatusTransitionException("Admin can only change product status to BANNED")
        }
        product.status = ProductStatus.BANNED
        return product.toDetailResponse(productImageStorage)
    }

    @Transactional(readOnly = true)
    fun issueUploadUrl(
        sellerId: Long,
        productId: Long,
        request: IssueProductImageUploadUrlRequest,
    ): IssueProductImageUploadUrlResponse {
        findOwnedProduct(productId, sellerId)
        validateUploadRequest(request)
        val issuedUpload =
            productImageStorage.issueUpload(
                productId = productId,
                command =
                    ProductImageUploadCommand(
                        type = request.type,
                        filename = request.filename.trim(),
                        contentType = request.contentType.trim(),
                        contentLength = request.contentLength,
                    ),
            )
        return issuedUpload.toResponse()
    }

    private fun findOwnedProduct(
        productId: Long,
        sellerId: Long,
    ): Product = productRepository.findOwnedWithImages(productId, sellerId) ?: throw ProductNotFoundException(productId)

    private fun requireExistingCategory(categoryId: Long) {
        categoryRepository.findByIdOrNull(categoryId) ?: throw CategoryNotFoundException(categoryId)
    }

    private fun validateImages(
        productId: Long,
        request: ProductImageGroupRequest,
    ): List<ProductImageSpec> {
        val groupedSpecs =
            listOf(
                ProductImageType.THUMBNAIL to request.thumbnailImages.map { it.toSpec(ProductImageType.THUMBNAIL) },
                ProductImageType.MAIN to request.mainImages.map { it.toSpec(ProductImageType.MAIN) },
                ProductImageType.DESCRIPTION to request.descriptionImages.map { it.toSpec(ProductImageType.DESCRIPTION) },
            )

        groupedSpecs.forEach { (type, specs) ->
            if (specs.map(ProductImageSpec::sortOrder).toSet().size != specs.size) {
                throw ProductImageValidationException("Duplicate sortOrder in $type images")
            }
        }

        val allKeys = groupedSpecs.flatMap { it.second }.map(ProductImageSpec::s3Key)
        if (allKeys.toSet().size != allKeys.size) {
            throw ProductImageValidationException("Duplicate s3Key in product image configuration")
        }

        val expectedPrefix = "product_images/$productId/"
        groupedSpecs.flatMap { it.second }.forEach { spec ->
            if (!spec.s3Key.startsWith(expectedPrefix)) {
                throw ProductImageValidationException("Image key must belong to product $productId")
            }
        }

        return groupedSpecs.flatMap { it.second }
    }

    private fun validateUploadRequest(request: IssueProductImageUploadUrlRequest) {
        val filename = request.filename.trim()
        val extension = filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (extension !in allowedExtensions) {
            throw ProductImageValidationException("Unsupported image extension: $extension")
        }

        val contentType = request.contentType.trim().lowercase()
        if (contentType !in allowedContentTypes) {
            throw ProductImageValidationException("Unsupported image content type: $contentType")
        }

        if (request.contentLength > maxUploadSizeInBytes) {
            throw ProductImageValidationException("Product image size must be 10MB or smaller")
        }
    }

    private fun ensureCanBeOnSale(
        product: Product,
        images: List<ProductImageSpec>,
    ) {
        if (product.stockQuantity <= 0) {
            throw ProductStatusTransitionException("Product with zero stock cannot be moved to ON_SALE")
        }

        val counts = images.groupingBy(ProductImageSpec::type).eachCount()
        ProductImageType.entries.forEach { type ->
            if ((counts[type] ?: 0) == 0) {
                throw ProductStatusTransitionException("Product must have at least one $type image to move to ON_SALE")
            }
        }
    }

    private fun applyInventoryStatus(product: Product) {
        if (product.status == ProductStatus.BANNED) {
            return
        }
        if (product.status == ProductStatus.ON_SALE && product.stockQuantity == 0) {
            product.status = ProductStatus.SOLD_OUT
        }
    }

    companion object {
        private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp")
        private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
        private const val maxUploadSizeInBytes = 10_485_760L
    }
}

private data class ProductImageSpec(
    val type: ProductImageType,
    val sortOrder: Int,
    val s3Key: String,
) {
    fun toEntity(): ProductImage =
        ProductImage(
            type = type,
            sortOrder = sortOrder,
            s3Key = s3Key,
        )
}

private fun ProductImageItemRequest.toSpec(type: ProductImageType): ProductImageSpec =
    ProductImageSpec(
        type = type,
        sortOrder = sortOrder,
        s3Key = s3Key.trim(),
    )

private fun ProductImage.toSpec(): ProductImageSpec =
    ProductImageSpec(
        type = type,
        sortOrder = sortOrder,
        s3Key = s3Key,
    )

private fun Product.toListResponse(productImageStorage: ProductImageStorage): ProductListResponse =
    ProductListResponse(
        id = requireNotNull(id),
        sellerId = sellerId,
        categoryId = categoryId,
        name = name,
        price = price,
        stockQuantity = stockQuantity,
        status = status,
        thumbnailImageUrl =
            images
                .asSequence()
                .filter { it.type == ProductImageType.THUMBNAIL }
                .sortedBy(ProductImage::sortOrder)
                .map { productImageStorage.resolvePublicUrl(it.s3Key) }
                .firstOrNull(),
    )

private fun Product.toDetailResponse(productImageStorage: ProductImageStorage): ProductDetailResponse =
    ProductDetailResponse(
        id = requireNotNull(id),
        sellerId = sellerId,
        categoryId = categoryId,
        name = name,
        price = price,
        stockQuantity = stockQuantity,
        status = status,
        images =
            ProductImageGroupResponse(
                thumbnailImages = images.toImageResponses(ProductImageType.THUMBNAIL, productImageStorage),
                mainImages = images.toImageResponses(ProductImageType.MAIN, productImageStorage),
                descriptionImages = images.toImageResponses(ProductImageType.DESCRIPTION, productImageStorage),
            ),
    )

private fun List<ProductImage>.toImageResponses(
    type: ProductImageType,
    productImageStorage: ProductImageStorage,
): List<ProductImageResponse> =
    filter { it.type == type }
        .sortedBy(ProductImage::sortOrder)
        .map {
            ProductImageResponse(
                type = it.type,
                sortOrder = it.sortOrder,
                url = productImageStorage.resolvePublicUrl(it.s3Key),
            )
        }

private fun IssuedProductImageUpload.toResponse(): IssueProductImageUploadUrlResponse =
    IssueProductImageUploadUrlResponse(
        s3Key = s3Key,
        uploadUrl = uploadUrl,
    )
