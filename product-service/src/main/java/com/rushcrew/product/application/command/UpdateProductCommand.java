package com.rushcrew.product.application.command;

import com.rushcrew.product.domain.vo.Category;

public record UpdateProductCommand(
    String companyName,
    String productName,
    String description,
    Long price,
    Category category,
    String imageUrl
) {

}
