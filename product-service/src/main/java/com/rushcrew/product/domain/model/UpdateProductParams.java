package com.rushcrew.product.domain.model;

import com.rushcrew.product.domain.vo.Category;

public record UpdateProductParams(
    String companyName,
    Category category,
    Long price,
    String productName,
    String description,
    String imageUrl
) {

}
