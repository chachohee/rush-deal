package com.rushcrew.product.application.result;

import com.rushcrew.product.domain.entity.Product;
import com.rushcrew.product.domain.vo.Category;

public record UpdateProductResult(
    String companyName,
    String productName,
    String description,
    Long price,
    Category category,
    String imageUrl
) {

    public static UpdateProductResult from(Product product) {
        return new UpdateProductResult(
            product.getCompanyName(),
            product.getProductInfo().getName(),
            product.getProductInfo().getDescription(),
            product.getPrice().getAmount(),
            product.getCategory(),
            product.getImageUrl()
        );
    }
}
