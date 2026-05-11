package com.rushcrew.timedeal.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rushcrew.common.entity.BaseEntity;
import com.rushcrew.timedeal.domain.vo.ProductItemIds;
import com.rushcrew.timedeal.domain.vo.TimeDealProductStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_time_deal_product", schema = "time_deal_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class TimeDealProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_deal_id", nullable = false)
    private TimeDeal timeDeal;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "productId", column = @Column(name = "product_id", nullable = false)),
        @AttributeOverride(name = "optionId", column = @Column(name = "product_option_id", nullable = false))
    })
    private ProductItemIds itemIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeDealProductStatus status;

    public static TimeDealProduct create( // 생성시에는 재고가 안 채워졌기 때문에 품절 상태
        TimeDeal timeDeal,
        ProductItemIds itemIds
    ) {
        return TimeDealProduct.builder()
            .timeDeal(timeDeal)
            .itemIds(itemIds)
            .status(TimeDealProductStatus.OUT_OF_STOCK)
            .build();
    }

    public void updateStatus(TimeDealProductStatus timeDealProductStatus) {
        this.status = timeDealProductStatus;
    }
}
