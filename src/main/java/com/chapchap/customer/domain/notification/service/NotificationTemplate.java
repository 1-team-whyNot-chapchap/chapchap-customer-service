package com.chapchap.customer.domain.notification.service;

import com.chapchap.customer.domain.notification.entity.NotificationType;

public record NotificationTemplate(NotificationType notificationType, String title, String content) {
}
