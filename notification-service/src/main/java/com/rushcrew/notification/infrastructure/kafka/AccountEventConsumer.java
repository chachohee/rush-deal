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
public class AccountEventConsumer {

    private static final String ROLE_LABEL_USER = "일반회원";
    private static final String ROLE_LABEL_SELLER = "판매자";
    private static final String ROLE_LABEL_MASTER = "관리자";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.account.event", groupId = "notification-service-group")
    public void onAccountEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long userId = node.get("userId").asLong();
            String type = node.get("type").asText();

            switch (type) {
                case "ACCOUNT_BLOCKED" -> notificationService.create(userId,
                    NotificationType.ACCOUNT_BLOCKED,
                    "계정이 정지되었습니다",
                    "관리자에 의해 계정이 정지되었습니다. 문의가 필요하면 고객센터로 연락해주세요.",
                    "/mypage");
                case "ACCOUNT_UNBLOCKED" -> notificationService.create(userId,
                    NotificationType.ACCOUNT_UNBLOCKED,
                    "계정 정지가 해제되었습니다",
                    "계정 사용이 다시 정상화되었습니다.",
                    "/mypage");
                case "ROLE_CHANGED" -> {
                    String newRole = node.get("newRole").asText();
                    notificationService.create(userId,
                        NotificationType.ROLE_CHANGED,
                        "계정 권한이 변경되었습니다",
                        "권한이 " + roleLabel(newRole) + "(으)로 변경되었습니다.",
                        "/mypage");
                }
                default -> log.warn("[Notification] 알 수 없는 account 이벤트 type={}", type);
            }
            log.info("[Notification] {} 알림 생성 - userId={}", type, userId);
        } catch (Exception e) {
            log.error("[Notification] user.account.event 처리 실패: {}", e.getMessage(), e);
        }
    }

    private String roleLabel(String role) {
        return switch (role) {
            case "USER" -> ROLE_LABEL_USER;
            case "SELLER" -> ROLE_LABEL_SELLER;
            case "MASTER" -> ROLE_LABEL_MASTER;
            default -> role;
        };
    }
}
