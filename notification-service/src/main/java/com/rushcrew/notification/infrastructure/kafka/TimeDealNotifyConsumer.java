package com.rushcrew.notification.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.notification.application.NotificationService;
import com.rushcrew.notification.domain.NotificationType;
import java.util.function.BiFunction;
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
        fanout(message, "interestedUserIds",
            NotificationType.TIMEDEAL_STARTED,
            (t, n) -> "관심 타임딜이 시작됐어요",
            (t, n) -> "\"" + t + "\" 타임딜이 시작됐습니다. 지금 확인해보세요!",
            NotificationType.SELLER_TIMEDEAL_STARTED,
            (t, n) -> "내 타임딜이 시작됐어요",
            (t, n) -> "\"" + t + "\" 타임딜이 시작됐습니다.");
    }

    @KafkaListener(topics = "timedeal.ending.soon", groupId = "notification-service-group")
    public void onTimeDealEndingSoon(String message) {
        fanout(message, "userIds",
            NotificationType.TIMEDEAL_ENDING_SOON,
            (t, n) -> "타임딜이 곧 종료돼요",
            (t, n) -> "\"" + t + "\" 타임딜이 " + n.get("minutesLeft").asInt() +
                "분 뒤 종료됩니다. 놓치기 전에 확인하세요!",
            null, null, null);
    }

    @KafkaListener(topics = "timedeal.sold.out.notify", groupId = "notification-service-group")
    public void onTimeDealSoldOut(String message) {
        fanout(message, "userIds",
            NotificationType.TIMEDEAL_SOLD_OUT,
            (t, n) -> "관심 타임딜이 매진됐어요",
            (t, n) -> "\"" + t + "\" 타임딜이 매진되었습니다.",
            NotificationType.SELLER_TIMEDEAL_SOLD_OUT,
            (t, n) -> "내 타임딜이 완판됐어요",
            (t, n) -> "\"" + t + "\" 타임딜 전 상품이 매진되었습니다.");
    }

    private void fanout(String message, String userIdsField,
                        NotificationType userType,
                        BiFunction<String, JsonNode, String> userTitleFn,
                        BiFunction<String, JsonNode, String> userBodyFn,
                        NotificationType sellerType,
                        BiFunction<String, JsonNode, String> sellerTitleFn,
                        BiFunction<String, JsonNode, String> sellerBodyFn) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String timeDealId = node.get("timeDealId").asText();
            String title = node.get("title").asText();
            String link = "/timedeals/" + timeDealId;

            int userCount = 0;
            JsonNode userIdsNode = node.get(userIdsField);
            if (userIdsNode != null && userIdsNode.isArray() && userType != null) {
                String t = userTitleFn.apply(title, node);
                String b = userBodyFn.apply(title, node);
                for (JsonNode uid : userIdsNode) {
                    notificationService.create(uid.asLong(), userType, t, b, link);
                    userCount++;
                }
            }

            JsonNode sellerNode = node.get("sellerId");
            boolean sellerNotified = false;
            if (sellerNode != null && !sellerNode.isNull() && sellerType != null) {
                notificationService.create(sellerNode.asLong(), sellerType,
                    sellerTitleFn.apply(title, node),
                    sellerBodyFn.apply(title, node),
                    link);
                sellerNotified = true;
            }

            log.info("[Notification] {} fanout - timeDealId={}, users={}, seller={}",
                userType, timeDealId, userCount, sellerNotified);
        } catch (Exception e) {
            log.error("[Notification] {} 처리 실패: {}", userType, e.getMessage(), e);
        }
    }
}
