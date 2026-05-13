package com.rushcrew.product.application.result;

import com.rushcrew.product.domain.entity.Product;
import java.util.List;
import java.util.UUID;

public record ProductDetailResult(
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

    public static ProductDetailResult of(Product product,
        List<ProductOptionResult> optionResultList) {
        return new ProductDetailResult(
            product.getId(),
            product.getSellerId().getId(),
            product.getCompanyName(),
            product.getProductInfo().getName(),
            product.getProductInfo().getDescription(),
            product.getPrice().getAmount(),
            product.getIsActive(),
            product.getCategory().name(),
            product.getImageUrl(),
            optionResultList
        );
    }
}

