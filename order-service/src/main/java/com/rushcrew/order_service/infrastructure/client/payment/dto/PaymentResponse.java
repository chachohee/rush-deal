package com.rushcrew.order_service.infrastructure.client.payment.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentResponse {
	private UUID paymentId;
	private UUID orderId;
	private boolean success;
	private String message;
}
