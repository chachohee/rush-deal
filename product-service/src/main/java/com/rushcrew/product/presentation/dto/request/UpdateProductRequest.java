package com.rushcrew.product.presentation.dto.request;

import com.rushcrew.product.application.command.UpdateProductCommand;
import com.rushcrew.product.domain.vo.Category;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProductRequest(
    String companyName,
    String productName,
    String description,
    @PositiveOrZero Long price,
    Category category,
    String imageUrl
) {

    public UpdateProductCommand toCommand() {
        return new UpdateProductCommand(
            this.companyName(),
            this.productName(),
            this.description(),
            this.price(),
            this.category(),
            this.imageUrl()
        );
    }
}
