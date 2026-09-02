package com.chapchap.customer.domain.notification.event;

import com.chapchap.customer.domain.notification.entity.Notification;

public record CustomerNotificationCreatedEvent(Notification notification) {
}
