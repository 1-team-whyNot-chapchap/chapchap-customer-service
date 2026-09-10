package com.chapchap.customer.domain.notification.dto;

import com.chapchap.customer.domain.notification.constant.NotificationType;

public record NotificationTemplate(NotificationType notificationType, String title, String content) {
}
