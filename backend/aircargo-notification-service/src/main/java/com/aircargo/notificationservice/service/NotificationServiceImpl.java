package com.aircargo.notificationservice.service;

import com.aircargo.notificationservice.config.NotificationStreamRegistry;
import com.aircargo.notificationservice.dto.NotificationDTO;
import com.aircargo.notificationservice.entity.Notification;
import com.aircargo.notificationservice.entity.NotificationType;
import com.aircargo.notificationservice.repository.NotificationRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository repository;
    private final NotificationStreamRegistry streamRegistry;

    public NotificationServiceImpl(NotificationRepository repository, NotificationStreamRegistry streamRegistry) {
        this.repository = repository;
        this.streamRegistry = streamRegistry;
    }

    @Override
    @Cacheable(value = "notifications", key = "#userId")
    public List<NotificationDTO> getNotificationsByUserId(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationDTO::fromEntity).toList();
    }

    @Override
    @Cacheable(value = "notifications-unread", key = "#userId")
    public List<NotificationDTO> getUnreadNotificationsByUserId(UUID userId) {
        return repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream().map(NotificationDTO::fromEntity).toList();
    }

    @Override
    public long getUnreadCount(UUID userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @CacheEvict(value = {"notifications", "notifications-unread"}, key = "#result.userId")
    public NotificationDTO markAsRead(UUID notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        notification.setRead(true);
        return NotificationDTO.fromEntity(repository.save(notification));
    }

    @Override
    @CacheEvict(value = {"notifications", "notifications-unread"}, allEntries = true)
    public void createNotification(UUID userId, String type, String title, String body,
                                    String entityType, UUID entityId) {
        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            notificationType = NotificationType.EMAIL;
        }
        Notification notification = new Notification(userId, notificationType, title, body, entityType, entityId);
        Notification saved = repository.save(notification);
        // Push en vivo (best-effort): un fallo de SSE nunca debe romper el guardado.
        try {
            streamRegistry.send(userId, NotificationDTO.fromEntity(saved));
        } catch (Exception e) {
            // silent — la notificación ya quedó persistida
        }
    }

    @Override
    @CacheEvict(value = {"notifications", "notifications-unread"}, allEntries = true)
    public void deleteNotification(UUID notificationId) {
        repository.deleteById(notificationId);
    }
}
