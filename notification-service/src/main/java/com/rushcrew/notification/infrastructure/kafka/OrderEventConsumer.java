package com.rushcrew.notification.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.notification.application.NotificationService;
import com.rushcrew.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.created", groupId = "notification-service-group")
    public void onOrderCreated(String message) {
        consume(message, NotificationType.ORDER_CREATED,
            (orderId) -> "주문이 접수되었습니다",
            (orderId) -> "주문 번호: " + shortId(orderId) + " 결제를 완료해주세요.",
            (orderId) -> "/orders/" + orderId);
    }

    @KafkaListener(topics = "order.paid", groupId = "notification-service-group")
    public void onOrderPaid(String message) {
        consume(message, NotificationType.ORDER_PAID,
            (orderId) -> "결제가 완료되었습니다",
            (orderId) -> "주문 " + shortId(orderId) + " 결제가 정상 처리되었습니다.",
            (orderId) -> "/orders/" + orderId);
    }

    @KafkaListener(topics = "order.cancelled", groupId = "notification-service-group")
    public void onOrderCancelled(String message) {
        consume(message, NotificationType.ORDER_CANCELED,
            (orderId) -> "주문이 취소되었습니다",
            (orderId) -> "주문 " + shortId(orderId) + " 이(가) 취소되었습니다.",
            (orderId) -> "/orders/" + orderId);
    }

    private void consume(String message, NotificationType type,
                         java.util.function.Function<String, String> titleFn,
                         java.util.function.Function<String, String> messageFn,
                         java.util.function.Function<String, String> linkFn) {
        try {
            JsonNode node = objectMapper.readTree(message);
            JsonNode userIdNode = node.get("userId");
            JsonNode orderIdNode = node.get("orderId");
            if (userIdNode == null || orderIdNode == null) {
                log.warn("[Notification] Missing userId/orderId in {}: {}", type, message);
                return;
            }
            Long userId = userIdNode.asLong();
            String orderId = orderIdNode.asText();
            notificationService.create(userId, type,
                titleFn.apply(orderId), messageFn.apply(orderId), linkFn.apply(orderId));
            log.info("[Notification] {} 알림 생성 userId={} orderId={}", type, userId, orderId);
        } catch (Exception e) {
            log.error("[Notification] {} 처리 실패: {}", type, e.getMessage(), e);
        }
    }

    private String shortId(String orderId) {
        return orderId.length() > 8 ? orderId.substring(0, 8) : orderId;
    }
}
