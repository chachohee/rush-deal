package com.rushcrew.order_service.infrastructure.client.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentRequest {
	private UUID orderId;
	private BigDecimal totalAmount;
}
