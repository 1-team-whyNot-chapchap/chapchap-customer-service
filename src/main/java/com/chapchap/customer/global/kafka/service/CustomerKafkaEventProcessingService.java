package com.chapchap.customer.global.kafka.service;

import com.chapchap.customer.domain.csreadmodel.service.CsReadModelProjectionService;
import com.chapchap.customer.domain.notification.service.NotificationCreationService;
import com.chapchap.customer.global.kafka.event.CustomerKafkaEvent;
import com.chapchap.customer.global.kafka.event.CustomerKafkaEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerKafkaEventProcessingService {
    private final CsReadModelProjectionService csReadModelProjectionService;
    private final NotificationCreationService notificationCreationService;

    @Transactional
    public void process(String messageKey, CustomerKafkaEvent event) {
        CustomerKafkaEventType eventType = CustomerKafkaEventType.from(event.eventType());
        if (eventType == null) {
            return;
        }
        if (eventType == CustomerKafkaEventType.DELIVERY_OPERATION_NOTIFICATION_REQUESTED) {
            notificationCreationService.createForDeliveryOperation(messageKey, event);
            return;
        }
        csReadModelProjectionService.project(messageKey, event);
        notificationCreationService.createForCustomerEvent(messageKey, event);
    }
}
