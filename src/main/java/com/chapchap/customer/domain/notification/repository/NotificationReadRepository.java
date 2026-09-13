package com.chapchap.customer.domain.notification.repository;

import com.chapchap.customer.domain.notification.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {
    boolean existsByNotification_IdAndReaderUserId(Long notificationId, Long readerUserId);

    // A current read is necessary after acquiring the notification lock under MySQL REPEATABLE READ.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<NotificationRead> findByNotification_IdAndReaderUserId(Long notificationId, Long readerUserId);
}
