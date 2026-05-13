package com.rushcrew.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rushcrew.product.application.command.CreateOptionCommand;
import com.rushcrew.product.application.command.CreateProductCommand;
import com.rushcrew.product.application.command.UpdateProductCommand;
import com.rushcrew.product.application.result.CreateProductResult;
import com.rushcrew.product.application.result.ProductDetailResult;
import com.rushcrew.product.application.service.ProductService;
import com.rushcrew.product.application.service.internal.ProductInternalService;
import com.rushcrew.product.domain.entity.Product;
import com.rushcrew.product.domain.repository.ProductRepository;
import com.rushcrew.product.domain.vo.Category;
import com.rushcrew.product.domain.vo.Price;
import com.rushcrew.product.domain.vo.ProductInfo;
import com.rushcrew.product.infrastructure.repostiory.ProductJpaRepository;
import com.rushcrew.product.presentation.internal.dto.ProductSearchInfoResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired ProductService productService;
    @Autowired ProductInternalService internalService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductJpaRepository productJpaRepository;

    @AfterEach
    void clean() {
        productJpaRepository.deleteAll();
    }

    private CreateProductResult createSampleProduct(Long sellerId) {
        return productService.createProduct(sellerId, "SELLER",
            new CreateProductCommand(
                sellerId,
                "나이키 코리아",
                ProductInfo.of("에어포스 1", "클래식 한정판"),
                Price.of(120000L),
                Category.SHOES,
                List.of(
                    new CreateOptionCommand("260", "WHITE"),
                    new CreateOptionCommand("270", "WHITE")
                )
            ));
    }

    @Test
    @DisplayName("createProduct: 상품 + 옵션 2건이 함께 저장되고 활성 상태로 시작한다")
    void create_persistsProductWithOptions() {
        CreateProductResult result = createSampleProduct(1L);

        Product saved = productRepository.findProductDetail(result.productId()).orElseThrow();
        assertThat(saved.getCompanyName()).isEqualTo("나이키 코리아");
        assertThat(saved.getCategory()).isEqualTo(Category.SHOES);
        assertThat(saved.getOptions()).hasSize(2);
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("disable/enable: 활성 토글이 정확히 반영된다")
    void disableEnable_togglesIsActive() {
        Long sellerId = 1L;
        CreateProductResult created = createSampleProduct(sellerId);

        productService.disableProduct(sellerId, "SELLER", created.productId());
        assertThat(productRepository.findById(created.productId()).orElseThrow().getIsActive()).isFalse();

        productService.enableProduct(sellerId, "SELLER", created.productId());
        assertThat(productRepository.findById(created.productId()).orElseThrow().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("다른 셀러는 본인 상품이 아니면 비활성화할 수 없다")
    void disable_byNonOwner_throws() {
        CreateProductResult created = createSampleProduct(1L);

        assertThatThrownBy(() ->
            productService.disableProduct(99L, "SELLER", created.productId()))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("MASTER는 다른 셀러의 상품을 삭제할 수 있다 (소프트 삭제)")
    void delete_byMaster_softDeletes() {
        CreateProductResult created = createSampleProduct(1L);

        productService.deleteProduct(999L, "MASTER", created.productId());

        assertThat(productRepository.findByIdAndDeletedAtIsNull(created.productId())).isEmpty();
    }

    @Test
    @DisplayName("getProductDetail: 옵션을 포함한 상세를 반환한다")
    void getProductDetail_returnsWithOptions() {
        CreateProductResult created = createSampleProduct(1L);

        ProductDetailResult detail = productService.getProductDetail(created.productId());

        assertThat(detail.productName()).isEqualTo("에어포스 1");
        assertThat(detail.companyName()).isEqualTo("나이키 코리아");
        assertThat(detail.price()).isEqualTo(120000L);
        assertThat(detail.productOptionsResult()).hasSize(2);
    }

    @Test
    @DisplayName("내부 API getProductSearchInfo: timedeal-service가 search 색인용으로 받아가는 페이로드")
    void internal_searchInfo_returnsForIndexing() {
        CreateProductResult created = createSampleProduct(1L);

        ProductSearchInfoResponse info = internalService.getProductSearchInfo(created.productId());

        assertThat(info.productId()).isEqualTo(created.productId());
        assertThat(info.productName()).isEqualTo("에어포스 1");
        assertThat(info.companyName()).isEqualTo("나이키 코리아");
        assertThat(info.category()).isEqualTo("SHOES");
    }

    @Test
    @DisplayName("updateProduct: 제목/설명/가격/카테고리를 한 번에 갱신한다")
    void update_changesProductFields() {
        CreateProductResult created = createSampleProduct(1L);

        productService.updateProduct(1L, "SELLER", created.productId(),
            new UpdateProductCommand("나이키", "리뉴얼 에어포스", "재출시", 110000L, Category.SHOES));

        Product after = productRepository.findById(created.productId()).orElseThrow();
        assertThat(after.getProductInfo().getName()).isEqualTo("리뉴얼 에어포스");
        assertThat(after.getPrice().getAmount()).isEqualTo(110000L);
    }
}
