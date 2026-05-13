package com.rushcrew.order_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rushcrew.order_service.domain.model.order.Order;
import com.rushcrew.order_service.domain.model.order.OrderItem;
import com.rushcrew.order_service.domain.enums.OrderStatus;
import com.rushcrew.order_service.domain.vo.ProductSnapshot;
import com.rushcrew.order_service.domain.vo.ShippingInfo;
import com.rushcrew.order_service.infrastructure.persistence.order.OrderJpaRepository;
import com.rushcrew.order_service.infrastructure.persistence.outbox.entity.OutboxEventEntity;
import com.rushcrew.order_service.infrastructure.persistence.outbox.entity.OutboxEventEntity.OutboxStatus;
import com.rushcrew.order_service.infrastructure.persistence.outbox.repository.OutboxEventJpaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderDomainIntegrationTest extends IntegrationTestBase {

    @Autowired OrderJpaRepository orderRepo;
    @Autowired OutboxEventJpaRepository outboxRepo;

    @AfterEach
    void clean() {
        orderRepo.deleteAll();
        outboxRepo.deleteAll();
    }

    private Order sampleOrder(Long userId) {
        ShippingInfo shipping = ShippingInfo.create(
            "차초희", "01012345678", "12345", "서울", "용산구 어딘가", null);
        ProductSnapshot snapshot = ProductSnapshot.builder()
            .timeDealStockId(UUID.randomUUID().toString())
            .productId(UUID.randomUUID().toString())
            .productName("에어포스 1")
            .productDescription("스니커즈")
            .optionId(UUID.randomUUID().toString())
            .optionName("260 / WHITE")
            .sellerId("1")
            .sellerName("나이키")
            .originalPrice(new BigDecimal("120000"))
            .timeDealId(UUID.randomUUID().toString())
            .timeDealTitle("타임딜")
            .discountRate(20)
            .category("SHOES")
            .build();
        OrderItem item = OrderItem.create(
            UUID.randomUUID(), 2L, new BigDecimal("96000"), snapshot);
        return Order.create(UUID.randomUUID(), userId, List.of(item), 0L, shipping);
    }

    @Test
    @DisplayName("Order.create는 PENDING 상태로 시작하고 합계 금액이 아이템 소계의 합")
    void create_startsAsPendingWithCorrectAmount() {
        Order o = sampleOrder(1L);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.getAmount().getTotalAmount()).isEqualByComparingTo("192000"); // 96000 * 2
        assertThat(o.getOrderItems()).hasSize(1);
    }

    @Test
    @DisplayName("cancelBeforePayment는 PENDING 상태에서만 가능")
    void cancelBeforePayment_requiresPending() {
        Order o = sampleOrder(1L);

        assertThat(o.canCancelBeforePayment()).isTrue();
        o.cancelBeforePayment("user-cancel");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> o.cancelBeforePayment("again"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("completePayment는 PENDING → PAID로 전이")
    void completePayment_transitionsToPaid() {
        Order o = sampleOrder(1L);

        o.completePayment();

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("refund는 PAID 또는 PURCHASE_CONFIRMED 상태에서만 가능")
    void refund_requiresPaidOrConfirmed() {
        Order o = sampleOrder(1L);

        assertThatThrownBy(() -> o.refund("aaa"))
            .isInstanceOf(RuntimeException.class);

        o.completePayment();
        assertThat(o.canRefund()).isTrue();
        o.refund("user-refund");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("isOwnedBy는 본인 userId와 일치하면 true")
    void isOwnedBy() {
        Order o = sampleOrder(100L);

        assertThat(o.isOwnedBy(100L)).isTrue();
        assertThat(o.isOwnedBy(101L)).isFalse();
    }

    @Test
    @DisplayName("OutboxEventEntity.create는 PENDING 상태로 시작하고 markAsPublished로 PUBLISHED 전이")
    void outbox_lifecycle() {
        OutboxEventEntity event = OutboxEventEntity.create(
            "ORDER", UUID.randomUUID(), "ORDER_CREATED", "{\"a\":1}");

        outboxRepo.save(event);
        OutboxEventEntity saved = outboxRepo.findById(event.getEventId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();

        saved.markAsPublished();
        outboxRepo.save(saved);

        assertThat(outboxRepo.findById(event.getEventId()).orElseThrow().getStatus())
            .isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("OutboxEvent markAsFailed는 retryCount를 올리고 FAILED 상태로")
    void outbox_failure_increments_retry() {
        OutboxEventEntity event = OutboxEventEntity.create(
            "ORDER", UUID.randomUUID(), "ORDER_PAID", "{}");
        outboxRepo.save(event);

        event.markAsFailed("kafka down");
        outboxRepo.save(event);

        OutboxEventEntity after = outboxRepo.findById(event.getEventId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(after.getRetryCount()).isEqualTo(1);
        assertThat(after.canRetry()).isTrue();
    }
}
