package com.rushcrew.product.application.result;

import com.rushcrew.product.domain.entity.Product;
import java.util.UUID;

public record CreateProductResult(
    UUID productId,
    String productName,
    String description,
    Long price,
    String imageUrl
) {

    public static CreateProductResult from(Product product) {
        return new CreateProductResult(
            product.getId(),
            product.getProductInfo().getName(),
            product.getProductInfo().getDescription(),
            product.getPrice().getAmount(),
            product.getImageUrl()
        );
    }
}
