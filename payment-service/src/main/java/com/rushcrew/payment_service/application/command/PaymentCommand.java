package com.rushcrew.payment_service.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCommand(
        UUID orderId,
        BigDecimal totalAmount
) {
}
