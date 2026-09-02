package com.chapchap.customer.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notifications_source_event", columnNames = "source_event_id"),
                @UniqueConstraint(name = "uk_notifications_business_key", columnNames = "business_key")
        }
)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 20)
    private NotificationRecipientType recipientType;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Column(name = "source_event_id", nullable = false, length = 36)
    private String sourceEventId;

    @Column(name = "business_key", length = 255)
    private String businessKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 60)
    private NotificationType notificationType;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "related_type", length = 40)
    private String relatedType;

    @Column(name = "related_id", length = 64)
    private String relatedId;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "delivery_slot", length = 10)
    private String deliverySlot;

    @Column(name = "reminder_stage", length = 10)
    private String reminderStage;

    @Column(name = "action_reason", length = 30)
    private String actionReason;

    @Column(name = "response_deadline", length = 35)
    private String responseDeadline;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    private Notification(
            NotificationRecipientType recipientType,
            Long recipientUserId,
            String sourceEventId,
            String businessKey,
            NotificationType notificationType,
            String title,
            String content,
            String relatedType,
            String relatedId,
            LocalDate deliveryDate,
            String deliverySlot,
            String reminderStage,
            String actionReason,
            String responseDeadline,
            LocalDateTime occurredAt,
            LocalDateTime createdAt
    ) {
        this.recipientType = recipientType;
        this.recipientUserId = recipientUserId;
        this.sourceEventId = sourceEventId;
        this.businessKey = businessKey;
        this.notificationType = notificationType;
        this.title = title;
        this.content = content;
        this.relatedType = relatedType;
        this.relatedId = relatedId;
        this.deliveryDate = deliveryDate;
        this.deliverySlot = deliverySlot;
        this.reminderStage = reminderStage;
        this.actionReason = actionReason;
        this.responseDeadline = responseDeadline;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public static Notification create(
            NotificationRecipientType recipientType,
            Long recipientUserId,
            String sourceEventId,
            String businessKey,
            NotificationType notificationType,
            String title,
            String content,
            String relatedType,
            String relatedId,
            LocalDate deliveryDate,
            String deliverySlot,
            String reminderStage,
            String actionReason,
            String responseDeadline,
            LocalDateTime occurredAt,
            LocalDateTime createdAt
    ) {
        return new Notification(recipientType, recipientUserId, sourceEventId, businessKey, notificationType,
                title, content, relatedType, relatedId, deliveryDate, deliverySlot, reminderStage, actionReason,
                responseDeadline, occurredAt, createdAt);
    }

    public Long getId() { return id; }
    public NotificationRecipientType getRecipientType() { return recipientType; }
    public Long getRecipientUserId() { return recipientUserId; }
    public String getSourceEventId() { return sourceEventId; }
    public String getBusinessKey() { return businessKey; }
    public NotificationType getNotificationType() { return notificationType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getRelatedType() { return relatedType; }
    public String getRelatedId() { return relatedId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
