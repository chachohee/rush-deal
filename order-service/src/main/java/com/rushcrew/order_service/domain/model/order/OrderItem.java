package com.rushcrew.order_service.domain.model.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.hibernate.annotations.Type;

import com.rushcrew.order_service.domain.common.BaseEntity;
import com.rushcrew.order_service.domain.vo.ProductSnapshot;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_order_item", schema = "order_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderItem extends BaseEntity {

	@Id
	private UUID orderItemId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Column(nullable = false)
	private UUID timeDealStockId;

	@Column(nullable = false)
	private Long quantity;

	@Column(precision = 12, scale = 2)
	private BigDecimal unitPrice; // 상품 원가

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal discountPrice; // 타임딜 할인가 (실제 판매가)

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	// @Column(nullable = false)
	// private UUID timeDealId;

	@Type(JsonBinaryType.class)
	@Column(columnDefinition = "jsonb")
	private ProductSnapshot productSnapshot;


	// ============================================
	//                 도메인 로직
	// ============================================

	// TODO: OrderItem 만들 때 ProductSnapShot 생성해서 주문 당시의 상품 정보가 같이 저장되도록
	public static OrderItem create(
		UUID timeDealStockId,
		Long quantity,
		// BigDecimal unitPrice,
		BigDecimal discountPrice,
		ProductSnapshot productSnapshot
	) {
		if (quantity == null || quantity <= 0) {
			throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
		}
		// if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
		// 	throw new IllegalArgumentException("상품 가격은 0보다 커야 합니다.");
		// }
		if (discountPrice == null || discountPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("할인가는 0 이상이어야 합니다.");
		}
		// if (discountPrice.compareTo(unitPrice) > 0) {
		// 	throw new IllegalArgumentException(
		// 		"할인가[%S] 는 원가[%s] 보다 클 수 없습니다.".formatted(discountPrice, unitPrice)
		// 	);
		// }
		if (productSnapshot == null) {
			throw new IllegalArgumentException("상품 스냅샷은 필수입니다.");
		}

		BigDecimal subtotal = discountPrice.multiply(BigDecimal.valueOf(quantity));

		return OrderItem.builder()
			.orderItemId(UUID.randomUUID())
			.timeDealStockId(timeDealStockId)
			.quantity(quantity)
			// .unitPrice(unitPrice)
			.discountPrice(discountPrice)
			.subtotal(subtotal)
			// .timeDealId(UUID.fromString(productSnapshot.timeDealId()))
			.productSnapshot(productSnapshot)
			.build();
	}

	// 주문 연관 관계 설정
	void assignOrder(Order order) {
		this.order = order;
	}

	public String getProductName() {
		return productSnapshot != null ? productSnapshot.productName() : null;
	}

	public String getProductDescription() {
		return productSnapshot != null ? productSnapshot.productDescription() : null;
	}

	// 할인율
	public BigDecimal getDiscountRate() {
		if (unitPrice.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO;
		}
		// (원가 - 할인가) / 원가 x 100
		BigDecimal discount = unitPrice.subtract(discountPrice);
		return discount
			.divide(unitPrice, 2, RoundingMode.HALF_UP)
			.multiply(BigDecimal.valueOf(100));
	}

	// 할인 금액
	public BigDecimal getTotalDiscount() {
		// (원가 - 할인가) x 수량
		return unitPrice.subtract(discountPrice).multiply(BigDecimal.valueOf(quantity));
	}

}
