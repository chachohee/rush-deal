package com.rushcrew.notification.application;

import com.rushcrew.notification.domain.Notification;
import com.rushcrew.notification.domain.NotificationType;
import java.time.Instant;

public record NotificationDto(
    Long id,
    NotificationType type,
    String title,
    String message,
    String link,
    boolean isRead,
    Instant createdAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(
            n.getId(),
            n.getType(),
            n.getTitle(),
            n.getMessage(),
            n.getLink(),
            n.isRead(),
            n.getCreatedAt()
        );
    }
}
