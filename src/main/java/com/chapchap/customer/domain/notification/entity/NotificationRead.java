package com.chapchap.customer.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notification_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_reads_reader", columnNames = {"notification_id", "reader_user_id"})
)
public class NotificationRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_read_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "reader_user_id", nullable = false)
    private Long readerUserId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    protected NotificationRead() {
    }

    private NotificationRead(Notification notification, Long readerUserId, LocalDateTime readAt) {
        this.notification = notification;
        this.readerUserId = readerUserId;
        this.readAt = readAt;
    }

    public static NotificationRead create(Notification notification, Long readerUserId, LocalDateTime readAt) {
        return new NotificationRead(notification, readerUserId, readAt);
    }
}
