package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.global.config.UuidStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "consultation_summary_jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_consultation_summary_jobs_consultation",
                        columnNames = "consultation_id"
                ),
                @UniqueConstraint(
                        name = "uk_consultation_summary_jobs_request",
                        columnNames = "request_id"
                )
        }
)
public class ConsultationSummaryJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consultation_summary_job_id")
    private Long id;

    @Column(name = "consultation_id", nullable = false)
    private Long consultationId;

    @Convert(converter = UuidStringConverter.class)
    @Column(name = "request_id", nullable = false, length = 36)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsultationSummaryJobStatus status;

    @Column(name = "terminal_fingerprint", length = 64)
    private String terminalFingerprint;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "is_retryable", nullable = false)
    private boolean retryable;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "callback_received_at")
    private LocalDateTime callbackReceivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ConsultationSummaryJob() {
    }

    private ConsultationSummaryJob(Long consultationId, UUID requestId, LocalDateTime now) {
        if (consultationId == null || consultationId <= 0) {
            throw new IllegalArgumentException("consultationId는 양수여야 합니다.");
        }
        this.consultationId = consultationId;
        this.requestId = Objects.requireNonNull(requestId);
        this.status = ConsultationSummaryJobStatus.PENDING;
        this.retryable = false;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static ConsultationSummaryJob create(
            Long consultationId,
            UUID requestId,
            LocalDateTime now
    ) {
        return new ConsultationSummaryJob(consultationId, requestId, now);
    }

    public void markSubmitted(LocalDateTime now) {
        if (!isTerminal()) {
            status = ConsultationSummaryJobStatus.SUBMITTED;
            submittedAt = Objects.requireNonNull(now);
            updatedAt = now;
        }
    }

    public void markAccepted(LocalDateTime now) {
        if (!isTerminal()) {
            status = ConsultationSummaryJobStatus.ACCEPTED;
            acceptedAt = Objects.requireNonNull(now);
            updatedAt = now;
        }
    }

    public void markSubmissionFailed(String code, boolean submissionRetryable, LocalDateTime now) {
        if (isTerminal()) {
            return;
        }
        if (code == null || code.isBlank() || code.length() > 100) {
            throw new IllegalArgumentException("요약 제출 실패 코드가 올바르지 않습니다.");
        }
        status = ConsultationSummaryJobStatus.SUBMISSION_FAILED;
        failureCode = code;
        retryable = submissionRetryable;
        updatedAt = Objects.requireNonNull(now);
    }

    public void applyCompleted(String fingerprint, LocalDateTime now) {
        terminalFingerprint = requireFingerprint(fingerprint);
        status = ConsultationSummaryJobStatus.COMPLETED;
        failureCode = null;
        retryable = false;
        callbackReceivedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void applyFailed(
            String fingerprint,
            ConsultationSummaryCallback.FailureCode callbackFailureCode,
            boolean callbackRetryable,
            LocalDateTime now
    ) {
        terminalFingerprint = requireFingerprint(fingerprint);
        status = ConsultationSummaryJobStatus.FAILED;
        failureCode = Objects.requireNonNull(callbackFailureCode).name();
        retryable = callbackRetryable;
        callbackReceivedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public boolean isTerminal() {
        return terminalFingerprint != null;
    }

    private String requireFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("terminal fingerprint 형식이 올바르지 않습니다.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getConsultationId() {
        return consultationId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public ConsultationSummaryJobStatus getStatus() {
        return status;
    }

    public String getTerminalFingerprint() {
        return terminalFingerprint;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
