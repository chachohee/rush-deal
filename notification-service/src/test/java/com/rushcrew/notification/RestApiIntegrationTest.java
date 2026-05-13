package com.rushcrew.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcrew.notification.application.NotificationDto;
import com.rushcrew.notification.application.NotificationService;
import com.rushcrew.notification.domain.Notification;
import com.rushcrew.notification.domain.NotificationRepository;
import com.rushcrew.notification.domain.NotificationType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

class RestApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    NotificationService service;

    @Autowired
    NotificationRepository repository;

    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    private HttpHeaders headers(Long userId) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Id", userId.toString());
        return h;
    }

    @Test
    @DisplayName("GET /api/v1/notifications - 본인 알림만 최신순으로 반환된다")
    void list_returnsOnlyOwnNotificationsDesc() {
        Long me = 1L, other = 2L;
        service.create(me, NotificationType.ORDER_CREATED, "주문 1", "msg", "/orders/1");
        service.create(other, NotificationType.ORDER_CREATED, "다른 사람 주문", "msg", "/orders/2");
        service.create(me, NotificationType.ORDER_PAID, "결제 완료", "msg", "/orders/1");

        ResponseEntity<List<NotificationDto>> res = rest.exchange(
            "/api/v1/notifications?size=10",
            HttpMethod.GET,
            new HttpEntity<>(headers(me)),
            new ParameterizedTypeReference<>() {});

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        List<NotificationDto> list = res.getBody();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).type()).isEqualTo(NotificationType.ORDER_PAID);
        assertThat(list.get(1).type()).isEqualTo(NotificationType.ORDER_CREATED);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count - 미읽음만 카운트한다")
    void unreadCount_excludesRead() {
        Long me = 3L;
        Notification n1 = service.create(me, NotificationType.ORDER_CREATED, "t", "m", null);
        service.create(me, NotificationType.ORDER_PAID, "t", "m", null);
        service.markRead(me, n1.getId());

        ResponseEntity<Map<String, Long>> res = rest.exchange(
            "/api/v1/notifications/unread-count",
            HttpMethod.GET,
            new HttpEntity<>(headers(me)),
            new ParameterizedTypeReference<>() {});

        assertThat(res.getBody().get("count")).isEqualTo(1L);
    }

    @Test
    @DisplayName("PATCH /{id}/read - 본인 알림은 읽음 처리, 남의 알림은 무시")
    void markRead_onlyOwnAffected() {
        Long me = 4L, other = 5L;
        Notification mine = service.create(me, NotificationType.ORDER_CREATED, "t", "m", null);
        Notification others = service.create(other, NotificationType.ORDER_CREATED, "t", "m", null);

        rest.exchange("/api/v1/notifications/" + mine.getId() + "/read",
            HttpMethod.PATCH, new HttpEntity<>(headers(me)), Void.class);
        rest.exchange("/api/v1/notifications/" + others.getId() + "/read",
            HttpMethod.PATCH, new HttpEntity<>(headers(me)), Void.class);

        assertThat(repository.findById(mine.getId()).get().isRead()).isTrue();
        assertThat(repository.findById(others.getId()).get().isRead()).isFalse();
    }

    @Test
    @DisplayName("PATCH /read-all - 본인의 미읽음 모두 읽음 처리")
    void markAllRead_affectsOnlyOwn() {
        Long me = 6L, other = 7L;
        service.create(me, NotificationType.ORDER_CREATED, "t", "m", null);
        service.create(me, NotificationType.ORDER_PAID, "t", "m", null);
        service.create(other, NotificationType.ORDER_CREATED, "t", "m", null);

        ResponseEntity<Map<String, Integer>> res = rest.exchange(
            "/api/v1/notifications/read-all",
            HttpMethod.PATCH,
            new HttpEntity<>(headers(me)),
            new ParameterizedTypeReference<>() {});

        assertThat(res.getBody().get("updated")).isEqualTo(2);
        assertThat(service.unreadCount(me)).isEqualTo(0);
        assertThat(service.unreadCount(other)).isEqualTo(1);
    }
}
