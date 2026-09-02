package com.chapchap.customer.domain.notification.service;

import com.chapchap.customer.domain.notification.entity.Notification;
import com.chapchap.customer.domain.notification.entity.NotificationRecipientType;
import com.chapchap.customer.domain.notification.entity.NotificationType;
import com.chapchap.customer.domain.notification.event.CustomerNotificationCreatedEvent;
import com.chapchap.customer.domain.notification.repository.NotificationRepository;
import com.chapchap.customer.global.kafka.event.CustomerKafkaEvent;
import com.chapchap.customer.global.kafka.event.CustomerKafkaEventType;
import com.chapchap.customer.global.kafka.exception.KafkaContractException;
import com.chapchap.customer.global.kafka.service.CustomerKafkaEventValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationCreationService {
    private final NotificationRepository notificationRepository;
    private final CustomerKafkaEventValidator eventValidator;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void createForCustomerEvent(String messageKey, CustomerKafkaEvent event) {
        CustomerKafkaEventType eventType = CustomerKafkaEventType.from(event.eventType());
        if (eventType == null || eventType == CustomerKafkaEventType.DELIVERY_OPERATION_NOTIFICATION_REQUESTED) {
            return;
        }
        eventValidator.validateEnvelope(event, false);
        if (notificationRepository.existsBySourceEventId(event.eventId())) {
            return;
        }

        CustomerNotificationCommand command = customerCommand(messageKey, event, eventType);
        if (command == null) {
            return;
        }
        Notification notification = notificationRepository.save(Notification.create(
                NotificationRecipientType.CUSTOMER,
                event.userId(),
                event.eventId(),
                null,
                command.template().notificationType(),
                command.template().title(),
                command.template().content(),
                command.relatedType(),
                command.relatedId(),
                null, null, null, null, null,
                event.occurredAtAsOffsetDateTime().toLocalDateTime(),
                LocalDateTime.now()
        ));
        applicationEventPublisher.publishEvent(new CustomerNotificationCreatedEvent(notification));
    }

    @Transactional
    public void createForDeliveryOperation(String messageKey, CustomerKafkaEvent event) {
        eventValidator.validateEnvelope(event, true);
        if (CustomerKafkaEventType.from(event.eventType()) != CustomerKafkaEventType.DELIVERY_OPERATION_NOTIFICATION_REQUESTED) {
            throw new KafkaContractException("Delivery 운영 알림 Event Type이 아닙니다.");
        }
        String recipientType = eventValidator.requiredText(event, "recipientType");
        String businessKey = eventValidator.requiredText(event, "businessKey");
        String referenceType = eventValidator.requiredText(event, "referenceType");
        String referenceId = eventValidator.requiredText(event, "referenceId");
        NotificationTemplate template = operationTemplate(eventValidator.requiredText(event, "notificationType"));
        if (notificationRepository.existsBySourceEventId(event.eventId())
                || notificationRepository.existsByBusinessKey(businessKey)) {
            return;
        }

        NotificationRecipientType recipient = parseOperationRecipient(recipientType);
        Long recipientUserId = recipient == NotificationRecipientType.RIDER
                ? eventValidator.requiredPositiveLong(event, "recipientUserId")
                : null;
        if (recipient == NotificationRecipientType.ADMIN && event.data().get("recipientUserId") != null) {
            throw new KafkaContractException("ADMIN 운영 알림의 recipientUserId는 null이어야 합니다.");
        }
        eventValidator.validateMessageKey(messageKey, recipient == NotificationRecipientType.ADMIN
                ? "ADMIN" : String.valueOf(recipientUserId));
        validateOperationConditions(event, template.notificationType());

        notificationRepository.save(Notification.create(
                recipient,
                recipientUserId,
                event.eventId(),
                businessKey,
                template.notificationType(),
                template.title(),
                template.content(),
                referenceType,
                referenceId,
                optionalDate(event, "deliveryDate"),
                optionalText(event, "deliverySlot"),
                optionalText(event, "reminderStage"),
                optionalText(event, "actionReason"),
                optionalText(event, "responseDeadline"),
                event.occurredAtAsOffsetDateTime().toLocalDateTime(),
                LocalDateTime.now()
        ));
    }

    private CustomerNotificationCommand customerCommand(
            String messageKey,
            CustomerKafkaEvent event,
            CustomerKafkaEventType eventType
    ) {
        return switch (eventType) {
            case PAYMENT_COMPLETED -> customerPaymentCompleted(messageKey, event);
            case PAYMENT_FAILED -> customerPaymentFailed(messageKey, event);
            case REFUND_COMPLETED -> customerCommand(messageKey, event, "refundId", "REFUND",
                    new NotificationTemplate(NotificationType.REFUND_COMPLETED, "환불 완료", "환불 처리가 완료되었습니다."));
            case REFUND_FAILED -> customerCommand(messageKey, event, "refundId", "REFUND",
                    new NotificationTemplate(NotificationType.REFUND_FAILED, "환불 처리 안내", "환불 처리에 실패했습니다."));
            case SUBSCRIPTION_SETTING_CHANGED -> subscriptionCommand(messageKey, event,
                    new NotificationTemplate(NotificationType.SUBSCRIPTION_SETTING_CHANGED, "구독 설정 변경", "구독 설정이 변경되었습니다."));
            case SUBSCRIPTION_CANCELLATION_CONFIRMED -> subscriptionCommand(messageKey, event,
                    new NotificationTemplate(NotificationType.SUBSCRIPTION_CANCELLATION_CONFIRMED, "구독 해지 확인", "구독 해지가 확인되었습니다."));
            case SUBSCRIPTION_ENDED -> subscriptionCommand(messageKey, event,
                    new NotificationTemplate(NotificationType.SUBSCRIPTION_ENDED, "구독 종료", "구독이 종료되었습니다."));
            case DELIVERY_ADDRESS_CHANGED -> customerCommand(messageKey, event, "deliveryAddressId", "DELIVERY_ADDRESS",
                    new NotificationTemplate(NotificationType.DELIVERY_ADDRESS_CHANGED, "배송지 변경", "배송지가 변경되었습니다."));
            case DELIVERY_ADDRESS_CHANGE_REJECTED -> customerCommand(messageKey, event, "deliveryAddressId", "DELIVERY_ADDRESS",
                    new NotificationTemplate(NotificationType.DELIVERY_ADDRESS_CHANGE_REJECTED, "배송지 변경 안내", "배송지 변경 요청이 반려되었습니다."));
            case DELIVERY_CREATED -> customerCommand(messageKey, event, "deliveryId", "DELIVERY",
                    new NotificationTemplate(NotificationType.DELIVERY_CREATED, "배송 준비", "배송이 준비되었습니다."));
            case DELIVERY_STARTED -> customerCommand(messageKey, event, "deliveryId", "DELIVERY",
                    new NotificationTemplate(NotificationType.DELIVERY_STARTED, "배송 시작", "배송이 시작되었습니다."));
            case DELIVERY_DELAYED -> customerCommand(messageKey, event, "deliveryId", "DELIVERY",
                    new NotificationTemplate(NotificationType.DELIVERY_DELAYED, "배송 지연 안내", "배송이 지연되고 있습니다."));
            case DELIVERY_COMPLETED -> customerCommand(messageKey, event, "deliveryId", "DELIVERY",
                    new NotificationTemplate(NotificationType.DELIVERY_COMPLETED, "배송 완료", "배송이 완료되었습니다."));
            case DELIVERY_FAILED -> customerCommand(messageKey, event, "deliveryId", "DELIVERY",
                    new NotificationTemplate(NotificationType.DELIVERY_FAILED, "배송 처리 안내", "배송 처리에 실패했습니다."));
            default -> null;
        };
    }

    private CustomerNotificationCommand customerPaymentCompleted(String messageKey, CustomerKafkaEvent event) {
        String paymentType = eventValidator.requiredText(event, "paymentType");
        NotificationTemplate template = switch (paymentType) {
            case "FIRST_SUBSCRIPTION_PAYMENT" -> new NotificationTemplate(
                    NotificationType.FIRST_SUBSCRIPTION_PAYMENT_COMPLETED, "첫 구독 결제 완료", "첫 구독 결제가 완료되었습니다."
            );
            case "REGULAR_PAYMENT" -> new NotificationTemplate(
                    NotificationType.REGULAR_PAYMENT_COMPLETED, "정기 결제 완료", "정기 결제가 완료되었습니다."
            );
            case "SETTING_CHANGE_PAYMENT" -> new NotificationTemplate(
                    NotificationType.SETTING_CHANGE_PAYMENT_COMPLETED, "설정 변경 결제 완료", "설정 변경 결제가 완료되었습니다."
            );
            default -> throw new KafkaContractException("PAYMENT_COMPLETED paymentType이 허용되지 않습니다.");
        };
        return customerCommand(messageKey, event, "paymentId", "PAYMENT", template);
    }

    private CustomerNotificationCommand customerPaymentFailed(String messageKey, CustomerKafkaEvent event) {
        String paymentType = eventValidator.requiredText(event, "paymentType");
        String paymentStatus = eventValidator.requiredText(event, "paymentStatus");
        NotificationTemplate template = switch (paymentStatus + ":" + paymentType) {
            case "RETRY_WAITING:REGULAR_PAYMENT" -> new NotificationTemplate(
                    NotificationType.REGULAR_PAYMENT_RETRY_WAITING, "정기 결제 재시도 예정", "정기 결제를 다시 시도할 예정입니다."
            );
            case "FAILED:REGULAR_PAYMENT" -> new NotificationTemplate(
                    NotificationType.REGULAR_PAYMENT_FINAL_FAILED, "정기 결제 실패", "정기 결제에 최종 실패했습니다."
            );
            case "FAILED:SETTING_CHANGE_PAYMENT" -> new NotificationTemplate(
                    NotificationType.SETTING_CHANGE_PAYMENT_FAILED, "설정 변경 결제 실패", "설정 변경 결제에 실패했습니다."
            );
            case "FAILED:FIRST_SUBSCRIPTION_PAYMENT" -> null;
            default -> throw new KafkaContractException("PAYMENT_FAILED 상태와 결제 유형 조합이 허용되지 않습니다.");
        };
        return template == null ? null : customerCommand(messageKey, event, "paymentId", "PAYMENT", template);
    }

    private CustomerNotificationCommand subscriptionCommand(String messageKey, CustomerKafkaEvent event, NotificationTemplate template) {
        String userId = String.valueOf(event.userId());
        eventValidator.validateMessageKey(messageKey, userId);
        return new CustomerNotificationCommand(template, "SUBSCRIPTION", userId);
    }

    private CustomerNotificationCommand customerCommand(
            String messageKey,
            CustomerKafkaEvent event,
            String idField,
            String relatedType,
            NotificationTemplate template
    ) {
        String relatedId = eventValidator.requiredText(event, idField);
        eventValidator.validateMessageKey(messageKey, relatedId);
        return new CustomerNotificationCommand(template, relatedType, relatedId);
    }

    private NotificationRecipientType parseOperationRecipient(String recipientType) {
        try {
            NotificationRecipientType recipient = NotificationRecipientType.valueOf(recipientType);
            if (recipient == NotificationRecipientType.CUSTOMER) {
                throw new KafkaContractException("Delivery 운영 알림 수신자는 RIDER 또는 ADMIN이어야 합니다.");
            }
            return recipient;
        } catch (IllegalArgumentException exception) {
            throw new KafkaContractException("Delivery 운영 알림 수신자가 허용되지 않습니다.");
        }
    }

    private NotificationTemplate operationTemplate(String notificationType) {
        try {
            NotificationType type = NotificationType.valueOf(notificationType);
            return switch (type) {
                case RIDER_ASSIGNMENT_AVAILABLE -> new NotificationTemplate(type, "배정 가능 업무", "배정 가능한 배송 업무가 있습니다.");
                case RIDER_ACKNOWLEDGEMENT_OPENED -> new NotificationTemplate(type, "배정 확인 필요", "배정 확인이 필요합니다.");
                case RIDER_ACKNOWLEDGEMENT_REMINDER -> new NotificationTemplate(type, "배정 확인 재알림", "배정 확인이 필요합니다.");
                case RIDER_REASSIGNED -> new NotificationTemplate(type, "배송 재배정", "배송 업무가 재배정되었습니다.");
                case ADMIN_LATE_ORDER_REVIEW -> new NotificationTemplate(type, "지연 주문 검토", "지연 주문 검토가 필요합니다.");
                case ADMIN_ASSIGNMENT_ACTION_REQUIRED -> new NotificationTemplate(type, "배정 조치 필요", "배송 배정 조치가 필요합니다.");
                case ADMIN_UNRESOLVED_DELIVERY -> new NotificationTemplate(type, "미해결 배송 확인", "미해결 배송 확인이 필요합니다.");
                case ADMIN_EVENT_PUBLISH_FAILED -> new NotificationTemplate(type, "이벤트 발행 실패", "Delivery 이벤트 발행 실패를 확인해야 합니다.");
                default -> throw new KafkaContractException("Delivery 운영 알림 유형이 허용되지 않습니다.");
            };
        } catch (IllegalArgumentException exception) {
            throw new KafkaContractException("Delivery 운영 알림 유형이 허용되지 않습니다.");
        }
    }

    private void validateOperationConditions(CustomerKafkaEvent event, NotificationType notificationType) {
        String reminderStage = optionalText(event, "reminderStage");
        String actionReason = optionalText(event, "actionReason");
        if (notificationType == NotificationType.RIDER_ACKNOWLEDGEMENT_REMINDER
                && !("FIRST".equals(reminderStage) || "FINAL".equals(reminderStage))) {
            throw new KafkaContractException("확인 재알림은 FIRST 또는 FINAL 단계가 필요합니다.");
        }
        if (notificationType == NotificationType.ADMIN_ASSIGNMENT_ACTION_REQUIRED
                && !("ISSUE_REPORTED".equals(actionReason) || "ACK_OVERDUE".equals(actionReason))) {
            throw new KafkaContractException("관리자 배정 조치 사유가 허용되지 않습니다.");
        }
        if (notificationType == NotificationType.RIDER_REASSIGNED
                && optionalText(event, "responseDeadline") == null) {
            throw new KafkaContractException("기사 재배정 알림에는 응답 마감 시각이 필요합니다.");
        }
    }

    private java.time.LocalDate optionalDate(CustomerKafkaEvent event, String fieldName) {
        String value = optionalText(event, fieldName);
        return value == null ? null : java.time.LocalDate.parse(value);
    }

    private String optionalText(CustomerKafkaEvent event, String fieldName) {
        Object value = event.data().get(fieldName);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private record CustomerNotificationCommand(NotificationTemplate template, String relatedType, String relatedId) {
    }
}
