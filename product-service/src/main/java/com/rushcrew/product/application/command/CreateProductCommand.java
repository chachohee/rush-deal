package com.rushcrew.product.application.command;

import com.rushcrew.product.domain.vo.Category;
import com.rushcrew.product.domain.vo.Price;
import com.rushcrew.product.domain.vo.ProductInfo;
import java.util.List;

public record CreateProductCommand(
    Long sellerId,
    String companyName,
    ProductInfo productInfo,
    Price price,
    Category category,
    String imageUrl,
    List<CreateOptionCommand> optionCommands
) {

}
