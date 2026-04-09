package com.rushcrew.order_service.infrastructure.client.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentPrepareResponse(
	UUID paymentId,
	String portOnePaymentId,
	BigDecimal amount,
	String status
) {}
