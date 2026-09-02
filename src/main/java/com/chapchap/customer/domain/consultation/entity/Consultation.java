package com.chapchap.customer.domain.consultation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "consultations")
public class Consultation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consultation_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsultationStatus status;

    @Column(length = 100)
    private String intent;

    @Column(length = 40)
    private String category;

    @Column(length = 20)
    private String priority;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Consultation() {
    }

    private Consultation(Long userId, LocalDateTime now) {
        this.userId = userId;
        this.status = ConsultationStatus.AI_HANDLING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Consultation create(Long userId, LocalDateTime now) {
        return new Consultation(userId, now);
    }

    public boolean requestAdminHandoff(LocalDateTime now) {
        if (status == ConsultationStatus.WAITING_ADMIN) {
            return false;
        }
        if (status != ConsultationStatus.AI_HANDLING) {
            throw new IllegalStateException("관리자 상담으로 전환할 수 없는 상태입니다.");
        }
        status = ConsultationStatus.WAITING_ADMIN;
        escalatedAt = now;
        updatedAt = now;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public ConsultationStatus getStatus() {
        return status;
    }

    public Long getAssignedAdminId() {
        return assignedAdminId;
    }

    public LocalDateTime getEscalatedAt() {
        return escalatedAt;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
