package com.chapchap.customer.domain.consultation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "consultation_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_consultation_messages_sequence", columnNames = {"consultation_id", "sequence_no"})
)
public class ConsultationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private ConsultationSenderType senderType;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ConsultationMessage() {
    }

    private ConsultationMessage(Consultation consultation, Long senderUserId, String content, int sequenceNo, LocalDateTime now) {
        this.consultation = consultation;
        this.senderType = ConsultationSenderType.USER;
        this.senderUserId = senderUserId;
        this.content = content;
        this.sequenceNo = sequenceNo;
        this.createdAt = now;
    }

    public static ConsultationMessage firstUserMessage(
            Consultation consultation,
            Long userId,
            String content,
            LocalDateTime now
    ) {
        return new ConsultationMessage(consultation, userId, content, 1, now);
    }

    public Long getId() {
        return id;
    }

    public ConsultationSenderType getSenderType() {
        return senderType;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
