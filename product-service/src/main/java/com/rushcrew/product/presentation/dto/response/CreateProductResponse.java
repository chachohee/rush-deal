package com.rushcrew.product.presentation.dto.response;

import com.rushcrew.product.application.result.CreateProductResult;
import java.util.UUID;

public record CreateProductResponse(
    UUID productId,
    String productName,
    String description,
    Long price
) {

    public static CreateProductResponse from(CreateProductResult result) {
        return new CreateProductResponse(
            result.productId(),
            result.productName(),
            result.description(),
            result.price()
        );
    }
}
