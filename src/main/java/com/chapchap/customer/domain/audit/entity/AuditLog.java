package com.chapchap.customer.domain.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private AuditActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private AuditActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private AuditTargetType targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditResult result;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    private AuditLog(
            Long actorUserId,
            AuditActionType actionType,
            String targetId,
            String traceId,
            AuditActorType actorType,
            AuditTargetType targetType,
            Map<String, Object> detail,
            LocalDateTime createdAt
    ) {
        this.actorUserId = actorUserId;
        this.actorType = actorType;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = AuditResult.SUCCESS;
        this.traceId = traceId;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public static AuditLog faqChange(
            Long actorUserId,
            AuditActionType actionType,
            String targetId,
            String traceId,
            Map<String, Object> detail,
            LocalDateTime createdAt
    ) {
        return new AuditLog(actorUserId, actionType, targetId, traceId, AuditActorType.ADMIN, AuditTargetType.FAQ, detail, createdAt);
    }

    public static AuditLog consultationChange(Long actorUserId, AuditActionType actionType, String targetId,
                                              String traceId, AuditActorType actorType, Map<String, Object> detail, LocalDateTime createdAt) {
        return new AuditLog(actorUserId, actionType, targetId, traceId, actorType, AuditTargetType.CONSULTATION, detail, createdAt);
    }

    public static AuditLog qualityInquiryChange(
            Long actorUserId,
            AuditActionType actionType,
            String targetId,
            String traceId,
            Map<String, Object> detail,
            LocalDateTime createdAt
    ) {
        return new AuditLog(
                actorUserId,
                actionType,
                targetId,
                traceId,
                AuditActorType.ADMIN,
                AuditTargetType.QUALITY_INQUIRY,
                detail,
                createdAt
        );
    }

    public static AuditLog knowledgeVersionChange(
            Long actorUserId,
            AuditActorType actorType,
            AuditActionType actionType,
            String targetId,
            String traceId,
            Map<String, Object> detail,
            LocalDateTime createdAt
    ) {
        return new AuditLog(
                actorUserId,
                actionType,
                targetId,
                traceId,
                actorType,
                AuditTargetType.KNOWLEDGE_VERSION,
                detail,
                createdAt
        );
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Long getId() {
        return id;
    }

    public AuditActorType getActorType() {
        return actorType;
    }

    public AuditActionType getActionType() {
        return actionType;
    }

    public AuditTargetType getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTraceId() {
        return traceId;
    }

    public AuditResult getResult() {
        return result;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
