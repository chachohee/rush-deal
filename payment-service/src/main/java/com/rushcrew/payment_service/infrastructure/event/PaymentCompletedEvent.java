package com.rushcrew.payment_service.infrastructure.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID paymentId,
        UUID orderId,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime completedAt,
        String eventType
) {
    public static PaymentCompletedEvent of(
            UUID paymentId,
            UUID orderId,
			BigDecimal totalAmount,
            String currency
    ) {
        return new PaymentCompletedEvent(
                paymentId,
                orderId,
                totalAmount,
                currency,
                LocalDateTime.now(),
                "PAYMENT_COMPLETED"
        );
    }
}
