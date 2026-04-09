package com.rushcrew.order_service.infrastructure.client.payment.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentCancelRequest {
	private UUID orderId;
	private Long userId;
}
