package com.chapchap.customer.domain.csreadmodel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "cs_read_models",
        uniqueConstraints = @UniqueConstraint(name = "uk_cs_read_models_projection", columnNames = {"projection_type", "aggregate_id"})
)
public class CsReadModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "read_model_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "projection_type", nullable = false, length = 30)
    private CsReadModelProjectionType projectionType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    // 외부 Domain이 소유하는 공개 상태 문자열은 Consumer가 임의 Enum으로 재해석하지 않는다.
    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "is_delayed")
    private Boolean delayed;

    @Column(name = "business_version", nullable = false)
    private Long businessVersion;

    @Column(name = "source_event_id", nullable = false, length = 36)
    private String sourceEventId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    protected CsReadModel() {
    }

    private CsReadModel(
            Long userId,
            CsReadModelProjectionType projectionType,
            String aggregateId,
            String status,
            Boolean delayed,
            Long businessVersion,
            String sourceEventId,
            LocalDateTime occurredAt,
            LocalDateTime syncedAt
    ) {
        this.userId = userId;
        this.projectionType = projectionType;
        this.aggregateId = aggregateId;
        this.status = status;
        this.delayed = delayed;
        this.businessVersion = businessVersion;
        this.sourceEventId = sourceEventId;
        this.occurredAt = occurredAt;
        this.syncedAt = syncedAt;
    }

    public static CsReadModel create(
            Long userId,
            CsReadModelProjectionType projectionType,
            String aggregateId,
            String status,
            Boolean delayed,
            Long businessVersion,
            String sourceEventId,
            LocalDateTime occurredAt,
            LocalDateTime syncedAt
    ) {
        return new CsReadModel(userId, projectionType, aggregateId, status, delayed, businessVersion,
                sourceEventId, occurredAt, syncedAt);
    }

    public boolean isNewerThan(Long incomingBusinessVersion) {
        return incomingBusinessVersion > businessVersion;
    }

    public void update(
            String status,
            Boolean delayed,
            Long businessVersion,
            String sourceEventId,
            LocalDateTime occurredAt,
            LocalDateTime syncedAt
    ) {
        this.status = status;
        this.delayed = delayed;
        this.businessVersion = businessVersion;
        this.sourceEventId = sourceEventId;
        this.occurredAt = occurredAt;
        this.syncedAt = syncedAt;
    }

    public void markDelayed(String sourceEventId, LocalDateTime occurredAt, LocalDateTime syncedAt) {
        this.delayed = true;
        this.sourceEventId = sourceEventId;
        this.occurredAt = occurredAt;
        this.syncedAt = syncedAt;
    }

    public Long getUserId() { return userId; }
    public CsReadModelProjectionType getProjectionType() { return projectionType; }
    public String getAggregateId() { return aggregateId; }
    public String getStatus() { return status; }
    public Boolean getDelayed() { return delayed; }
    public Long getBusinessVersion() { return businessVersion; }
}
