package com.rushcrew.product.domain.entity;

import com.rushcrew.common.entity.BaseEntity;
import com.rushcrew.product.domain.model.CreateProductParams;
import com.rushcrew.product.domain.model.UpdateProductParams;
import com.rushcrew.product.domain.vo.Category;
import com.rushcrew.product.domain.vo.Price;
import com.rushcrew.product.domain.vo.ProductInfo;
import com.rushcrew.product.domain.vo.SellerId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_product", schema = "product_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "seller_id", nullable = false))
    private SellerId sellerId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "product_name", nullable = false)),
        @AttributeOverride(name = "description", column = @Column(name = "description", nullable = false))
    })
    private ProductInfo productInfo;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Price price;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "image_url")
    private String imageUrl;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductOption> options = new ArrayList<>();

    public static Product create(CreateProductParams params) {
        Product product = Product.builder()
            .sellerId(params.sellerId())
            .companyName(params.companyName())
            .productInfo(params.productInfo())
            .price(params.price())
            .category(params.category())
            .imageUrl(params.imageUrl())
            .build();

        params.optionCommands().forEach(option ->
            product.addOption(option.size(), option.color()));

        return product;
    }

    public ProductOption addOption(String size, String color) {
        ProductOption option = ProductOption.of(this, size, color);
        this.options.add(option);
        return option;
    }

    public void update(UpdateProductParams params) {
        if (params.companyName() != null) {
            this.companyName = params.companyName();
        }
        if (params.category() != null) {
            this.category = params.category();
        }
        updateProductInfo(params.productName(), params.description());
        this.price = params.price() != null ? Price.of(params.price()) : this.price;
        if (params.imageUrl() != null) {
            this.imageUrl = params.imageUrl();
        }
    }

    private void updateProductInfo(String newName, String newDescription) {
        if (newName == null && newDescription == null) {
            return;
        }
        String updatedName = newName != null ? newName : this.productInfo.getName();
        String updatedDescription =
            newDescription != null ? newDescription : this.productInfo.getDescription();

        this.productInfo = ProductInfo.of(updatedName, updatedDescription);
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void delete(Long userId) {
        this.deactivate();
        this.softDelete(userId);
        this.getOptions().forEach(option -> option.softDelete(userId));
    }
}
