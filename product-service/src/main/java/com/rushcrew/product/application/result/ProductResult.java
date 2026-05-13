package com.rushcrew.product.application.result;

import com.rushcrew.product.domain.entity.Product;
import java.util.UUID;

public record ProductResult(
    UUID productId,
    String companyName,
    String productName,
    String description,
    Long price,
    String imageUrl
) {

    public static ProductResult from(Product product) {
        return new ProductResult(
            product.getId(),
            product.getCompanyName(),
            product.getProductInfo().getName(),
            product.getProductInfo().getDescription(),
            product.getPrice().getAmount(),
            product.getImageUrl()
        );
    }
}
