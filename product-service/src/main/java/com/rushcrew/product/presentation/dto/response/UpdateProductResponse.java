package com.rushcrew.product.presentation.dto.response;

import com.rushcrew.product.application.result.UpdateProductResult;
import com.rushcrew.product.domain.vo.Category;

public record UpdateProductResponse(
    String companyName,
    String productName,
    String description,
    Long price,
    Category category,
    String imageUrl
) {

    public static UpdateProductResponse from(UpdateProductResult result) {
        return new UpdateProductResponse(
            result.companyName(),
            result.productName(),
            result.description(),
            result.price(),
            result.category(),
            result.imageUrl()
        );
    }
}
