package com.rushcrew.product.application.service.impl;

import com.rushcrew.product.application.ProductFilter;
import com.rushcrew.product.application.command.CreateProductCommand;
import com.rushcrew.product.application.command.UpdateProductCommand;
import com.rushcrew.product.application.result.CreateProductResult;
import com.rushcrew.product.application.result.ProductDetailResult;
import com.rushcrew.product.application.result.ProductOptionResult;
import com.rushcrew.product.application.result.ProductResult;
import com.rushcrew.product.application.result.UpdateProductResult;
import com.rushcrew.product.application.service.ProductService;
import com.rushcrew.product.application.service.ProductPolicy;
import com.rushcrew.product.domain.entity.Product;
import com.rushcrew.product.domain.model.CreateProductParams;
import com.rushcrew.product.domain.model.UpdateProductParams;
import com.rushcrew.product.domain.repository.ProductRepository;
import com.rushcrew.product.domain.vo.SellerId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPolicy productPolicy;

    @Override
    @Transactional
    public CreateProductResult createProduct(
        Long userId, String role, CreateProductCommand command
    ) {
        SellerId sellerId = productPolicy.decideSellerId(command.sellerId(), userId, role);

        CreateProductParams params = new CreateProductParams(
            sellerId, command.companyName(), command.productInfo(),
            command.price(), command.category(), command.imageUrl(),
            command.optionCommands()
        );

        Product product = Product.create(params);
        Product newProduct = productRepository.save(product);
        return CreateProductResult.from(newProduct);
    }

    @Override
    @Transactional
    public UpdateProductResult updateProduct(
        Long userId, String role, UUID productId, UpdateProductCommand command
    ) {
        Product product = productPolicy.findAndValidateProduct(productId);
        productPolicy.validateSellerPermission(product, userId, role);

        UpdateProductParams params = new UpdateProductParams(
            command.companyName(), command.category(), command.price(),
            command.productName(), command.description(), command.imageUrl()
        );
        product.update(params);

        return UpdateProductResult.from(product);
    }

    @Override
    @Transactional
    public void disableProduct(Long userId, String role, UUID productId) {
        Product product = productPolicy.findAndValidateProduct(productId);
        productPolicy.validateSellerPermission(product, userId, role);
        product.deactivate();
    }

    @Override
    @Transactional
    public void enableProduct(Long userId, String role, UUID productId) {
        Product product = productPolicy.findAndValidateProduct(productId);
        productPolicy.validateSellerPermission(product, userId, role);
        product.activate();
    }

    @Override
    @Transactional
    public void deleteProduct(Long userId, String role, UUID productId) {
        Product product = productPolicy.findAndValidateProduct(productId);
        productPolicy.validateSellerPermission(product, userId, role);

        product.delete(userId);
    }

    @Override
    public Page<ProductResult> getProducts(ProductFilter productFilter, Pageable pageable) {
        return productRepository.searchEnabledProducts(productFilter, pageable);
    }

    @Override
    public ProductDetailResult getProductDetail(UUID productId) {
        Product product = productRepository.findProductDetail(productId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));

        List<ProductOptionResult> optionResultList =
            product.getOptions().stream().map(ProductOptionResult::from).toList();

        return ProductDetailResult.of(product, optionResultList);
    }
}
