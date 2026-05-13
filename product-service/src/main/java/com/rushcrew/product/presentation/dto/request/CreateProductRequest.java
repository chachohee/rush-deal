package com.rushcrew.product.presentation.dto.request;

import com.rushcrew.product.application.command.CreateProductCommand;
import com.rushcrew.product.domain.vo.Category;
import com.rushcrew.product.domain.vo.Price;
import com.rushcrew.product.domain.vo.ProductInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record CreateProductRequest(

    Long sellerId, // MASTER만 채우는 값 (판매자ID 지정을 위해 받아오는 값)

    @NotBlank
    String companyName,

    @NotBlank
    String productName,

    @NotBlank
    String description,

    @NotNull @PositiveOrZero
    Long price,

    @NotNull
    Category category,

    String imageUrl,

    @NotEmpty @Valid
    List<CreateOptionRequest> optionRequests
) {

    public CreateProductCommand toCommand() {
        return new CreateProductCommand(
            this.sellerId(),
            this.companyName(),
            ProductInfo.of(this.productName(), this.description()),
            Price.of(this.price()),
            this.category(),
            this.imageUrl(),
            this.optionRequests().stream().map(CreateOptionRequest::toCommand).toList()
        );
    }
}
