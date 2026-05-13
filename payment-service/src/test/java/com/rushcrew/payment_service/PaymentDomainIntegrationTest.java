package com.rushcrew.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rushcrew.payment_service.domain.model.Payment;
import com.rushcrew.payment_service.domain.repository.PaymentRepository;
import com.rushcrew.payment_service.domain.vo.PaymentStatus;
import com.rushcrew.payment_service.infrastructure.repository.PaymentJpaRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentDomainIntegrationTest extends IntegrationTestBase {

    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentJpaRepository jpaRepository;

    @AfterEach
    void clean() {
        jpaRepository.deleteAll();
    }

    private Payment savedPending(BigDecimal amount) {
        return paymentRepository.save(Payment.create(
            UUID.randomUUID(), amount, UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("Payment.create는 PENDING 상태로 시작한다")
    void create_startsAsPending() {
        Payment p = savedPending(new BigDecimal("50000"));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getPaymentId()).isNotNull();
    }

    @Test
    @DisplayName("completePayment는 PENDING → PAID 전이만 허용한다")
    void complete_transitionsToPaid() {
        Payment p = savedPending(new BigDecimal("50000"));

        p.completePayment();

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);

        assertThatThrownBy(p::completePayment)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("결제 요청 상태");
    }

    @Test
    @DisplayName("cancelPayment는 PAID → CANCELLED 전이만 허용한다")
    void cancel_requiresPaid() {
        Payment p = savedPending(new BigDecimal("50000"));

        assertThatThrownBy(p::cancelPayment)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("결제 완료 상태");

        p.completePayment();
        p.cancelPayment();

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("findByOrderId는 저장된 결제를 조회한다")
    void findByOrderId_returnsSaved() {
        UUID orderId = UUID.randomUUID();
        Payment created = paymentRepository.save(Payment.create(
            orderId, new BigDecimal("30000"), UUID.randomUUID().toString()));

        Payment found = paymentRepository.findByOrderId(orderId).orElseThrow();

        assertThat(found.getPaymentId()).isEqualTo(created.getPaymentId());
        assertThat(found.getAmount()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("findByPortonePaymentId는 PortOne ID로 조회된다")
    void findByPortone_lookupKey() {
        String portoneId = "portone-" + UUID.randomUUID();
        paymentRepository.save(Payment.create(
            UUID.randomUUID(), new BigDecimal("99000"), portoneId));

        assertThat(paymentRepository.findByPortonePaymentId(portoneId)).isPresent();
        assertThat(paymentRepository.findByPortonePaymentId("not-existing")).isEmpty();
    }

    @Test
    @DisplayName("verifyPaymentOrThrow: 금액과 통화가 다르면 예외")
    void verifyPayment_mismatch_throws() {
        Payment p = savedPending(new BigDecimal("50000"));

        assertThatThrownBy(() -> p.verifyPaymentOrThrow(50000L, "USD"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.verifyPaymentOrThrow(40000L, "KRW"))
            .isInstanceOf(IllegalArgumentException.class);

        // 일치하면 예외 없음
        p.verifyPaymentOrThrow(50000L, "KRW");
    }
}
