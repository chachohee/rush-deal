package com.rushcrew.payment_service.domain.model;

import com.rushcrew.common.entity.BaseEntity;
import com.rushcrew.payment_service.domain.vo.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Table(name = "p_payment", schema = "payment_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID paymentId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "portone_payment_id")
    private String portonePaymentId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    public static Payment create(UUID orderId, BigDecimal amount, String portonePaymentId) {
        Payment payment = new Payment();

        payment.orderId = orderId;
        payment.amount = amount;
        payment.portonePaymentId = portonePaymentId;
        payment.status = PaymentStatus.PENDING;

        return payment;
    }

    public void completePayment() {
        if (!status.isPending()) {
            throw new IllegalArgumentException("결제 요청 상태에서만 완료할 수 있습니다.");
        }
        this.status = PaymentStatus.PAID;
    }

    public void cancelPayment() {
        if (!status.isPaid()) {
            throw new IllegalArgumentException("결제 완료 상태에서만 취소할 수 있습니다.");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public void verifyPaymentOrThrow(Long amount, String currency) {
        if (!verifyAmount(amount)) {
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }
        if (!verifyCurrency(currency)) {
            throw new IllegalArgumentException("지원하지 않는 통화입니다:" + currency);
        }
    }
    private boolean verifyAmount(Long amount) {
        if (this.amount.longValue() != amount) {
            return false;
        }
        return true;
    }
    public boolean verifyCurrency(String currency) {
        if (!"KRW".equals(currency)) {
            return false;
        }
        return true;
    }
}