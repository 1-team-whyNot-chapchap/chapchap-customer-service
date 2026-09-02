package com.chapchap.customer.domain.csreadmodel.service;

import com.chapchap.customer.domain.csreadmodel.entity.CsReadModel;
import com.chapchap.customer.domain.csreadmodel.entity.CsReadModelProjectionType;
import com.chapchap.customer.domain.csreadmodel.repository.CsReadModelRepository;
import com.chapchap.customer.global.kafka.event.CustomerKafkaEvent;
import com.chapchap.customer.global.kafka.event.CustomerKafkaEventType;
import com.chapchap.customer.global.kafka.service.CustomerKafkaEventValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CsReadModelProjectionService {
    private final CsReadModelRepository csReadModelRepository;
    private final CustomerKafkaEventValidator eventValidator;

    @Transactional
    public void project(String messageKey, CustomerKafkaEvent event) {
        CustomerKafkaEventType eventType = CustomerKafkaEventType.from(event.eventType());
        if (eventType == null || !isProjectionEvent(eventType)) {
            return;
        }
        eventValidator.validateEnvelope(event, false);
        switch (eventType) {
            case PAYMENT_COMPLETED -> upsert(
                    messageKey, event, CsReadModelProjectionType.PAYMENT,
                    eventValidator.requiredText(event, "paymentId"), "COMPLETED",
                    false, eventValidator.requiredPositiveLong(event, "paymentVersion")
            );
            case PAYMENT_FAILED -> upsert(
                    messageKey, event, CsReadModelProjectionType.PAYMENT,
                    eventValidator.requiredText(event, "paymentId"),
                    paymentFailureStatus(event), false, eventValidator.requiredPositiveLong(event, "paymentVersion")
            );
            case PAYMENT_RETRY_STOPPED -> upsert(
                    messageKey, event, CsReadModelProjectionType.PAYMENT,
                    eventValidator.requiredText(event, "paymentId"), "RETRY_STOPPED",
                    false, eventValidator.requiredPositiveLong(event, "paymentVersion")
            );
            case SUBSCRIPTION_STATUS_CHANGED -> upsert(
                    messageKey, event, CsReadModelProjectionType.SUBSCRIPTION,
                    String.valueOf(event.userId()), eventValidator.requiredText(event, "subscriptionStatus"),
                    false, eventValidator.requiredPositiveLong(event, "subscriptionVersion")
            );
            case DELIVERY_ADDRESS_CHANGED -> upsert(
                    messageKey, event, CsReadModelProjectionType.DELIVERY_ADDRESS,
                    eventValidator.requiredText(event, "deliveryAddressId"), "CHANGED",
                    false, eventValidator.requiredPositiveLong(event, "deliveryAddressVersion")
            );
            case DELIVERY_CREATED -> upsert(
                    messageKey, event, CsReadModelProjectionType.DELIVERY,
                    eventValidator.requiredText(event, "deliveryId"), "READY",
                    false, eventValidator.requiredPositiveLong(event, "deliveryVersion")
            );
            case DELIVERY_STARTED -> upsertDeliveryState(messageKey, event, "DELIVERING");
            case DELIVERY_COMPLETED -> upsertDeliveryState(messageKey, event, "DELIVERED");
            case DELIVERY_FAILED -> upsertDeliveryState(messageKey, event, "FAILED");
            case DELIVERY_DELAYED -> markDeliveryDelayed(messageKey, event);
            default -> {
                // 이 분기에서 처리하지 않는 계약 Event는 다른 Consumer 책임이다.
            }
        }
    }

    private boolean isProjectionEvent(CustomerKafkaEventType eventType) {
        return switch (eventType) {
            case PAYMENT_COMPLETED, PAYMENT_FAILED, PAYMENT_RETRY_STOPPED,
                 SUBSCRIPTION_STATUS_CHANGED, DELIVERY_ADDRESS_CHANGED,
                 DELIVERY_CREATED, DELIVERY_STARTED, DELIVERY_DELAYED,
                 DELIVERY_COMPLETED, DELIVERY_FAILED -> true;
            default -> false;
        };
    }

    private String paymentFailureStatus(CustomerKafkaEvent event) {
        String paymentStatus = eventValidator.requiredText(event, "paymentStatus");
        if (!paymentStatus.equals("RETRY_WAITING") && !paymentStatus.equals("FAILED")) {
            throw new com.chapchap.customer.global.kafka.exception.KafkaContractException(
                    "PAYMENT_FAILED paymentStatus가 허용되지 않습니다."
            );
        }
        return paymentStatus;
    }

    private void upsertDeliveryState(String messageKey, CustomerKafkaEvent event, String status) {
        String deliveryId = eventValidator.requiredText(event, "deliveryId");
        upsert(messageKey, event, CsReadModelProjectionType.DELIVERY, deliveryId, status,
                existingDelayed(CsReadModelProjectionType.DELIVERY, deliveryId),
                eventValidator.requiredPositiveLong(event, "deliveryVersion"));
    }

    private Boolean existingDelayed(CsReadModelProjectionType projectionType, String aggregateId) {
        return csReadModelRepository.findByProjectionTypeAndAggregateId(projectionType, aggregateId)
                .map(CsReadModel::getDelayed)
                .orElse(null);
    }

    private void markDeliveryDelayed(String messageKey, CustomerKafkaEvent event) {
        String deliveryId = eventValidator.requiredText(event, "deliveryId");
        eventValidator.validateMessageKey(messageKey, deliveryId);
        csReadModelRepository.findByProjectionTypeAndAggregateId(CsReadModelProjectionType.DELIVERY, deliveryId)
                .ifPresent(readModel -> readModel.markDelayed(
                        event.eventId(), event.occurredAtAsOffsetDateTime().toLocalDateTime(), LocalDateTime.now()
                ));
    }

    private void upsert(
            String messageKey,
            CustomerKafkaEvent event,
            CsReadModelProjectionType projectionType,
            String aggregateId,
            String status,
            Boolean delayed,
            long businessVersion
    ) {
        eventValidator.validateMessageKey(messageKey, aggregateId);
        LocalDateTime occurredAt = event.occurredAtAsOffsetDateTime().toLocalDateTime();
        LocalDateTime now = LocalDateTime.now();
        csReadModelRepository.findByProjectionTypeAndAggregateId(projectionType, aggregateId)
                .ifPresentOrElse(
                        readModel -> {
                            if (readModel.isNewerThan(businessVersion)) {
                                readModel.update(status, delayed, businessVersion, event.eventId(), occurredAt, now);
                            }
                        },
                        () -> csReadModelRepository.save(CsReadModel.create(
                                event.userId(), projectionType, aggregateId, status, delayed, businessVersion,
                                event.eventId(), occurredAt, now
                        ))
                );
    }
}
