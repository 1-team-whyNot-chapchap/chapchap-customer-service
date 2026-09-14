package com.chapchap.customer.domain.notification.repository;

import com.chapchap.customer.domain.notification.entity.Notification;
import com.chapchap.customer.domain.notification.constant.NotificationRecipientType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsBySourceEventId(String sourceEventId);

    boolean existsByBusinessKey(String businessKey);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findByIdAndRecipientTypeAndRecipientUserId(
            Long notificationId,
            NotificationRecipientType recipientType,
            Long recipientUserId
    );

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findByIdAndRecipientTypeAndRecipientUserIdIsNull(
            Long notificationId,
            NotificationRecipientType recipientType
    );

    List<Notification> findByRecipientTypeAndRecipientUserIdOrderByOccurredAtDescIdDesc(
            NotificationRecipientType recipientType,
            Long recipientUserId
    );

    List<Notification> findByRecipientTypeAndRecipientUserIdIsNullOrderByOccurredAtDescIdDesc(
            NotificationRecipientType recipientType
    );
}
