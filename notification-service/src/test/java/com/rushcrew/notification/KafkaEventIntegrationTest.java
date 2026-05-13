package com.rushcrew.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.rushcrew.notification.domain.Notification;
import com.rushcrew.notification.domain.NotificationRepository;
import com.rushcrew.notification.domain.NotificationType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaEventIntegrationTest extends IntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    NotificationRepository notificationRepository;

    @AfterEach
    void clean() {
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("order.created 이벤트 수신 시 ORDER_CREATED 알림이 생성된다")
    void onOrderCreated_createsNotification() {
        Long userId = 100L;
        String orderId = "11111111-2222-3333-4444-555555555555";
        String payload = """
            {"orderId":"%s","userId":%d,"status":"PENDING","totalAmount":50000}
            """.formatted(orderId, userId);

        kafkaTemplate.send("order.created", orderId, payload);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> list = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10));
            assertThat(list).hasSize(1);
            Notification n = list.get(0);
            assertThat(n.getType()).isEqualTo(NotificationType.ORDER_CREATED);
            assertThat(n.getTitle()).contains("주문이 접수");
            assertThat(n.getLink()).contains(orderId);
            assertThat(n.isRead()).isFalse();
        });
    }

    @Test
    @DisplayName("order.paid 이벤트 수신 시 ORDER_PAID 알림이 생성된다")
    void onOrderPaid_createsNotification() {
        Long userId = 101L;
        String orderId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String payload = """
            {"orderId":"%s","userId":%d}
            """.formatted(orderId, userId);

        kafkaTemplate.send("order.paid", orderId, payload);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> list = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10));
            assertThat(list).hasSize(1);
            assertThat(list.get(0).getType()).isEqualTo(NotificationType.ORDER_PAID);
            assertThat(list.get(0).getTitle()).contains("결제가 완료");
        });
    }

    @Test
    @DisplayName("user.account.event BLOCKED 수신 시 ACCOUNT_BLOCKED 알림이 생성된다")
    void onAccountBlocked_createsNotification() {
        Long userId = 102L;
        String payload = """
            {"userId":%d,"type":"ACCOUNT_BLOCKED"}
            """.formatted(userId);

        kafkaTemplate.send("user.account.event", userId.toString(), payload);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> list = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10));
            assertThat(list).hasSize(1);
            assertThat(list.get(0).getType()).isEqualTo(NotificationType.ACCOUNT_BLOCKED);
        });
    }

    @Test
    @DisplayName("user.account.event ROLE_CHANGED 수신 시 ROLE_CHANGED 알림이 새 역할 라벨과 함께 생성된다")
    void onRoleChanged_createsNotificationWithRoleLabel() {
        Long userId = 103L;
        String payload = """
            {"userId":%d,"type":"ROLE_CHANGED","newRole":"SELLER"}
            """.formatted(userId);

        kafkaTemplate.send("user.account.event", userId.toString(), payload);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> list = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10));
            assertThat(list).hasSize(1);
            Notification n = list.get(0);
            assertThat(n.getType()).isEqualTo(NotificationType.ROLE_CHANGED);
            assertThat(n.getMessage()).contains("판매자");
        });
    }

    @Test
    @DisplayName("timedeal.start.notify 수신 시 관심 사용자에게 TIMEDEAL_STARTED, 셀러에게 SELLER_TIMEDEAL_STARTED 알림이 생성된다")
    void onTimeDealStartNotify_fansOutToUsersAndSeller() {
        long sellerId = 999L;
        List<Long> userIds = List.of(201L, 202L);
        String payload = """
            {
              "timeDealId":"77777777-7777-7777-7777-777777777777",
              "title":"테스트 타임딜",
              "sellerId":%d,
              "interestedUserIds":[%d,%d]
            }
            """.formatted(sellerId, userIds.get(0), userIds.get(1));

        kafkaTemplate.send("timedeal.start.notify",
            "77777777-7777-7777-7777-777777777777", payload);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userIds.get(0), PageRequest.of(0, 10))).hasSize(1);
            assertThat(notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userIds.get(1), PageRequest.of(0, 10))).hasSize(1);
            List<Notification> sellerNotifs = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(sellerId, PageRequest.of(0, 10));
            assertThat(sellerNotifs).hasSize(1);
            assertThat(sellerNotifs.get(0).getType())
                .isEqualTo(NotificationType.SELLER_TIMEDEAL_STARTED);
        });
    }
}
