package com.rushcrew.order_service.application.query.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rushcrew.order_service.domain.vo.ShippingInfo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderDetailDto {
	private UUID orderId;
	private Long userId;
	private String orderStatus;
	private BigDecimal totalAmount;
	private Long pointUsed;
	private BigDecimal finalAmount;
	private Instant orderedAt;
	private Instant paymentCompletedAt;
	private Instant purchaseConfirmedAt;
	private Instant cancelledAt;
	private Instant autoConfirmScheduledAt;
	private ShippingInfo shippingInfo;
	private List<OrderItemQueryDto> orderItems;

	@JsonCreator
	public OrderDetailDto(
		@JsonProperty("orderId") UUID orderId,
		@JsonProperty("userId") Long userId,
		@JsonProperty("orderStatus") String orderStatus,
		@JsonProperty("totalAmount") BigDecimal totalAmount,
		@JsonProperty("pointUsed") Long pointUsed,
		@JsonProperty("finalAmount") BigDecimal finalAmount,
		@JsonProperty("orderedAt") Instant orderedAt,
		@JsonProperty("paymentCompletedAt") Instant paymentCompletedAt,
		@JsonProperty("purchaseConfirmedAt") Instant purchaseConfirmedAt,
		@JsonProperty("cancelledAt") Instant cancelledAt,
		@JsonProperty("autoConfirmScheduledAt") Instant autoConfirmScheduledAt,
		@JsonProperty("shippingInfo") ShippingInfo shippingInfo,
		@JsonProperty("orderItems") List<OrderItemQueryDto> orderItems
	) {
		this.orderId = orderId;
		this.userId = userId;
		this.orderStatus = orderStatus;
		this.totalAmount = totalAmount;
		this.pointUsed = pointUsed;
		this.finalAmount = finalAmount;
		this.orderedAt = orderedAt;
		this.paymentCompletedAt = paymentCompletedAt;
		this.purchaseConfirmedAt = purchaseConfirmedAt;
		this.cancelledAt = cancelledAt;
		this.autoConfirmScheduledAt = autoConfirmScheduledAt;
		this.shippingInfo = shippingInfo;
		this.orderItems = orderItems != null ? orderItems : new ArrayList<>();
	}
}
