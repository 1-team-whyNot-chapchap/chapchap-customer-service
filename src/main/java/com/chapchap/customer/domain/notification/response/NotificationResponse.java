package com.chapchap.customer.domain.notification.response;

import com.chapchap.customer.domain.notification.entity.Notification;
import com.chapchap.customer.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType notificationType,
        String title,
        String content,
        String relatedType,
        String relatedId,
        LocalDateTime occurredAt,
        boolean read
) {
    public static NotificationResponse from(Notification notification, boolean read) {
        return new NotificationResponse(
                notification.getId(), notification.getNotificationType(), notification.getTitle(), notification.getContent(),
                notification.getRelatedType(), notification.getRelatedId(), notification.getOccurredAt(), read
        );
    }
}
