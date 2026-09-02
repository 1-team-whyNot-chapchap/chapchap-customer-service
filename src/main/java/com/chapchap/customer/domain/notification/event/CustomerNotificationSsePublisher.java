package com.chapchap.customer.domain.notification.event;

import com.chapchap.customer.domain.notification.service.CustomerNotificationSseService;
import com.chapchap.customer.domain.notification.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerNotificationSsePublisher {
    private final CustomerNotificationSseService customerNotificationSseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(CustomerNotificationCreatedEvent event) {
        Long recipientUserId = event.notification().getRecipientUserId();
        if (recipientUserId == null) {
            return;
        }
        try {
            customerNotificationSseService.publish(recipientUserId, NotificationResponse.from(event.notification(), false));
        } catch (RuntimeException exception) {
            log.warn("고객 알림 SSE 전달에 실패했습니다. notificationId={}", event.notification().getId(), exception);
        }
    }
}
