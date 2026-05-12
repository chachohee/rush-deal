package com.rushcrew.product.application.service.internal;

import com.rushcrew.product.presentation.internal.dto.ProductInfoResponse;
import com.rushcrew.product.presentation.internal.dto.ProductSearchInfoResponse;
import java.util.UUID;

public interface ProductInternalService {

    ProductInfoResponse getProductItemIds(UUID uuid);

    ProductSearchInfoResponse getProductSearchInfo(UUID productId);
}
