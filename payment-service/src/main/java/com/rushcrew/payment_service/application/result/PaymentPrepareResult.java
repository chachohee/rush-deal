package com.rushcrew.payment_service.application.result;

import com.rushcrew.payment_service.domain.model.Payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentPrepareResult(
        UUID paymentId,
        String portOnePaymentId,
        BigDecimal amount,
        String status
) {
    public static PaymentPrepareResult of(String portOnePaymentId, Payment payment) {
        return new PaymentPrepareResult(
                payment.getPaymentId(),
                portOnePaymentId,
                payment.getAmount(),
                payment.getStatus().getDescription()
        );
    }
}
