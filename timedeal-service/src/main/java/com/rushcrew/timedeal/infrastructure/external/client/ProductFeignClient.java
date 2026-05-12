package com.rushcrew.timedeal.infrastructure.external.client;

import com.rushcrew.timedeal.infrastructure.external.dto.ProductInfoDTO;
import com.rushcrew.timedeal.infrastructure.external.dto.ProductSearchInfoDTO;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", path = "/internal/v1/products")
public interface ProductFeignClient {

    @GetMapping("/{productId}/info")
    ProductInfoDTO getProductItemIds(
        @PathVariable UUID productId
    );

    @GetMapping("/{productId}/search-info")
    ProductSearchInfoDTO getProductSearchInfo(
        @PathVariable UUID productId
    );
}
