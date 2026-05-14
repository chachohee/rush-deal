package com.rushcrew.payment_service.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
    UUID orderId,
    BigDecimal totalAmount,
    BigDecimal finalAmount,
    String orderStatus
) {
}
