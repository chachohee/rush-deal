package com.rushcrew.timedeal.infrastructure.external.adapter;

import com.rushcrew.timedeal.application.model.ProductInfo;
import com.rushcrew.timedeal.application.model.ProductSearchInfo;
import com.rushcrew.timedeal.domain.port.ProductClient;
import com.rushcrew.timedeal.infrastructure.external.client.ProductFeignClient;
import com.rushcrew.timedeal.infrastructure.external.dto.ProductInfoDTO;
import com.rushcrew.timedeal.infrastructure.external.dto.ProductSearchInfoDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductFeignAdapter implements ProductClient {

    private final ProductFeignClient productFeignClient;

    @Override
    public ProductInfo getProductItemIds(UUID productId) {
        ProductInfoDTO productInfo = productFeignClient.getProductItemIds(productId);
        return ProductInfo.of(productInfo.optionIds(), productInfo.sellerId(), productInfo.price());
    }

    @Override
    public ProductSearchInfo getProductSearchInfo(UUID productId) {
        ProductSearchInfoDTO dto = productFeignClient.getProductSearchInfo(productId);
        return new ProductSearchInfo(dto.productId(), dto.productName(), dto.companyName(), dto.category());
    }
}
