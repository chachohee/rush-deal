package com.rushcrew.product.application.service.internal;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.product.domain.entity.Product;
import com.rushcrew.product.domain.exception.ProductErrorCode;
import com.rushcrew.product.domain.repository.ProductRepository;
import com.rushcrew.product.presentation.internal.dto.ProductInfoResponse;
import com.rushcrew.product.presentation.internal.dto.ProductSearchInfoResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductInternalServiceImpl implements ProductInternalService {

    private final ProductRepository productRepository;

    @Override
    public ProductInfoResponse getProductItemIds(UUID productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.NOT_FOUND_PRODUCT));

        List<UUID> optionIds = new ArrayList<>();
        product.getOptions().forEach(option -> optionIds.add(option.getId()));
        return ProductInfoResponse.of(
            product.getId(), optionIds,
            product.getSellerId().getId(), product.getPrice().getAmount());
    }

    @Override
    public ProductSearchInfoResponse getProductSearchInfo(UUID productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.NOT_FOUND_PRODUCT));

        return new ProductSearchInfoResponse(
            product.getId(),
            product.getProductInfo().getName(),
            product.getCompanyName(),
            product.getCategory().name()
        );
    }
}
