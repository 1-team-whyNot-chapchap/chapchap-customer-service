package com.chapchap.customer.domain.notification.repository;

import com.chapchap.customer.domain.notification.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {
    boolean existsByNotification_IdAndReaderUserId(Long notificationId, Long readerUserId);
}
