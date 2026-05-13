package com.rushcrew.product.presentation.dto.response;

import com.rushcrew.product.application.result.ProductDetailResult;
import com.rushcrew.product.application.result.ProductOptionResult;
import java.util.List;
import java.util.UUID;

public record ProductDetailResponse(
    UUID productId,
    Long sellerId,
    String companyName,
    String productName,
    String description,
    Long price,
    Boolean isActive,
    String category,
    String imageUrl,
    List<ProductOptionResult> productOptionsResult
) {

    public static ProductDetailResponse from(ProductDetailResult result) {
        return new ProductDetailResponse(
            result.productId(),
            result.sellerId(),
            result.companyName(),
            result.productName(),
            result.description(),
            result.price(),
            result.isActive(),
            result.category(),
            result.imageUrl(),
            result.productOptionsResult()
        );
    }
}

