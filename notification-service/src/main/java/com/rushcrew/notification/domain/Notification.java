package com.rushcrew.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "p_notification",
    schema = "notification_schema",
    indexes = {
        @Index(name = "idx_notification_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_notification_user_unread", columnList = "user_id, is_read")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 255)
    private String link;

    @Column(name = "is_read", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean isRead;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Notification create(Long userId, NotificationType type,
                                      String title, String message, String link) {
        return Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .message(message)
            .link(link)
            .isRead(false)
            .createdAt(Instant.now())
            .build();
    }

    public void markRead() {
        this.isRead = true;
    }
}
