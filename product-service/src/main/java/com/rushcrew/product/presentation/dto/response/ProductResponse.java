package com.rushcrew.product.presentation.dto.response;

import com.rushcrew.product.application.result.ProductResult;
import java.util.UUID;

public record ProductResponse(
    UUID productId,
    String companyName,
    String productName,
    String description,
    Long price,
    String imageUrl
) {

    public static ProductResponse from(ProductResult result) {
        return new ProductResponse(
            result.productId(),
            result.companyName(),
            result.productName(),
            result.description(),
            result.price(),
            result.imageUrl()
        );
    }
}

