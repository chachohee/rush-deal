package com.rushcrew.product.domain.model;

import com.rushcrew.product.application.command.CreateOptionCommand;
import com.rushcrew.product.domain.vo.Category;
import com.rushcrew.product.domain.vo.Price;
import com.rushcrew.product.domain.vo.ProductInfo;
import com.rushcrew.product.domain.vo.SellerId;
import java.util.List;

public record CreateProductParams(
    SellerId sellerId,
    String companyName,
    ProductInfo productInfo,
    Price price,
    Category category,
    String imageUrl,
    List<CreateOptionCommand> optionCommands
) {

}
