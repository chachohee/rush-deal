// package com.rushcrew.payment_service.infrastructure.repository;
//
// import com.rushcrew.payment_service.domain.model.Payment;
// import com.rushcrew.payment_service.domain.vo.PaymentStatus;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
// import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
// import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
//
// import java.math.BigDecimal;
// import java.util.UUID;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// @DataJpaTest
// @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// class PaymentJpaRepositoryTest {
//
//     @Autowired
//     private PaymentJpaRepository paymentJpaRepository;
//
//     @Autowired
//     private TestEntityManager entityManager;
//
//     @Test
//     @DisplayName("결제 저장 테스트")
//     void savePayment() {
//         UUID orderId = UUID.randomUUID();
//         Long amount = 10000L;
//         String portOnePaymentId = UUID.randomUUID().toString();
//
//         Payment payment = Payment.create(orderId, amount, portOnePaymentId);
//
//         Payment savedPayment = paymentJpaRepository.save(payment);
//
//         assertNotNull(savedPayment);
//         assertNotNull(savedPayment.getPaymentId());
//         assertEquals(savedPayment.getOrderId(), orderId);
//         assertEquals(savedPayment.getAmount(), amount);
//         assertEquals(savedPayment.getStatus(), PaymentStatus.PENDING);
//     }
// }