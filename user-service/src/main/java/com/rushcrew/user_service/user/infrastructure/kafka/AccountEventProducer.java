package com.rushcrew.user_service.user.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventProducer {

    private static final String TOPIC = "user.account.event";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishRoleChanged(Long userId, String newRole) {
        publish(userId, "ROLE_CHANGED", Map.of("newRole", newRole));
    }

    public void publishBlocked(Long userId) {
        publish(userId, "ACCOUNT_BLOCKED", Map.of());
    }

    public void publishUnblocked(Long userId) {
        publish(userId, "ACCOUNT_UNBLOCKED", Map.of());
    }

    private void publish(Long userId, String type, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("userId", userId);
            payload.put("type", type);
            payload.putAll(extra);
            kafkaTemplate.send(TOPIC, userId.toString(), objectMapper.writeValueAsString(payload));
            log.info("[Account] 이벤트 발행 - userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.error("[Account] 이벤트 발행 실패 - userId={}, type={}", userId, type, e);
        }
    }
}
