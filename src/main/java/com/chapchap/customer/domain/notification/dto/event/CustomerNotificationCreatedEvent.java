package com.chapchap.customer.domain.notification.dto.event;

import com.chapchap.customer.domain.notification.entity.Notification;

public record CustomerNotificationCreatedEvent(Notification notification) {
}
