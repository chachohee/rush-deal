package com.rushcrew.notification.presentation;

import com.rushcrew.notification.application.NotificationDto;
import com.rushcrew.notification.application.NotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(
        @RequestHeader("X-User-Id") Long userId,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.list(userId, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
        @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(Map.of("count", service.unreadCount(userId)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
        @RequestHeader("X-User-Id") Long userId,
        @PathVariable Long id
    ) {
        service.markRead(userId, id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(
        @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(Map.of("updated", service.markAllRead(userId)));
    }
}
