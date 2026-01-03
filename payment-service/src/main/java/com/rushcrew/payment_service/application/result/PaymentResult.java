package com.rushcrew.payment_service.application.result;

import com.rushcrew.payment_service.domain.model.Payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResult(
        UUID paymentId,
        UUID orderId,
        BigDecimal totalAmount,
        String status
) {
    public static PaymentResult from(Payment payment) {
        return new PaymentResult(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus().getDescription()
        );
    }
}
