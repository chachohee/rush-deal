package com.rushcrew.notification.application;

import com.rushcrew.notification.domain.Notification;
import com.rushcrew.notification.domain.NotificationRepository;
import com.rushcrew.notification.domain.NotificationType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Notification create(Long userId, NotificationType type,
                               String title, String message, String link) {
        Notification saved = repository.save(Notification.create(userId, type, title, message, link));
        try {
            messagingTemplate.convertAndSendToUser(
                String.valueOf(userId), "/queue/notifications", NotificationDto.from(saved));
        } catch (Exception e) {
            log.warn("[Notification] WS push 실패 userId={}: {}", userId, e.getMessage());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(Long userId, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
            .stream().map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        repository.findById(notificationId).ifPresent(n -> {
            if (!n.getUserId().equals(userId)) return;
            n.markRead();
        });
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId);
    }
}
