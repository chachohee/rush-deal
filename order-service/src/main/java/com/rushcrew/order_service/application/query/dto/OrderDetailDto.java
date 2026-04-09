package com.rushcrew.order_service.application.query.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rushcrew.order_service.domain.vo.ShippingInfo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonDeserialize(builder = OrderDetailDto.OrderDetailDtoBuilder.class)
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

	public OrderDetailDto(
		UUID orderId,
		Long userId,
		String orderStatus,
		BigDecimal totalAmount,
		Long pointUsed,
		BigDecimal finalAmount,
		Instant orderedAt,
		Instant paymentCompletedAt,
		Instant purchaseConfirmedAt,
		Instant cancelledAt,
		Instant autoConfirmScheduledAt,
		ShippingInfo shippingInfo,
		List<OrderItemQueryDto> orderItems
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

	@JsonPOJOBuilder(withPrefix = "")
	public static class OrderDetailDtoBuilder {
	}
}
