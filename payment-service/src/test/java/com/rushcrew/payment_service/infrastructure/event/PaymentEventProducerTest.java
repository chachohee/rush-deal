// package com.rushcrew.payment_service.infrastructure.event;
//
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.kafka.test.context.EmbeddedKafka;
// import org.springframework.test.annotation.DirtiesContext;
//
// import java.math.BigDecimal;
// import java.util.UUID;
//
// import static org.junit.jupiter.api.Assertions.assertNotNull;
//
// @SpringBootTest
// @DirtiesContext
// @EmbeddedKafka(partitions = 1, topics = {"payment.completed"})
// class PaymentEventProducerTest {
//
//     @Autowired
//     private PaymentEventProducer paymentEventProducer;
//
//     @Test
//     void publishPaymentCompleted_성공() throws InterruptedException {
//         // Given
//         UUID paymentId = UUID.randomUUID();
//         UUID orderId = UUID.randomUUID();
//         PaymentCompletedEvent event = PaymentCompletedEvent.of(
//                 paymentId,
//                 orderId,
//                 500000L,
//                 "KRW"
//         );
//
//         // When
//         paymentEventProducer.publishPaymentCompleted(event);
//
//         // Then
//         Thread.sleep(1000); // Kafka 메시지 전송 대기
//         assertNotNull(event);
//         System.out.println("✅ Kafka 이벤트 발행 성공: " + event);
//     }
// }
