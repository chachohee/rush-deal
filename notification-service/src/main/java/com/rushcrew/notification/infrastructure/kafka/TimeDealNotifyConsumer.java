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
public class TimeDealNotifyConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "timedeal.start.notify", groupId = "notification-service-group")
    public void onTimeDealStartNotify(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String timeDealId = node.get("timeDealId").asText();
            String title = node.get("title").asText();
            JsonNode userIdsNode = node.get("interestedUserIds");
            if (userIdsNode == null || !userIdsNode.isArray()) {
                log.warn("[Notification] interestedUserIds 누락: {}", message);
                return;
            }

            String notifyTitle = "관심 타임딜이 시작됐어요";
            String notifyMessage = "\"" + title + "\" 타임딜이 시작됐습니다. 지금 확인해보세요!";
            String link = "/timedeals/" + timeDealId;

            for (JsonNode uid : userIdsNode) {
                Long userId = uid.asLong();
                notificationService.create(userId, NotificationType.TIMEDEAL_STARTED,
                    notifyTitle, notifyMessage, link);
            }
            log.info("[Notification] TIMEDEAL_STARTED fanout 완료 - timeDealId={}, users={}",
                timeDealId, userIdsNode.size());
        } catch (Exception e) {
            log.error("[Notification] timedeal.start.notify 처리 실패: {}", e.getMessage(), e);
        }
    }
}
