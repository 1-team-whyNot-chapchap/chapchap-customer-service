package com.chapchap.customer.domain.knowledge.processing.async;

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
        name = "knowledge_processing_attempts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_knowledge_processing_attempts_job_attempt",
                        columnNames = {"knowledge_processing_job_id", "attempt"}
                ),
                @UniqueConstraint(
                        name = "uk_knowledge_processing_attempts_request",
                        columnNames = "request_id"
                )
        }
)
public class KnowledgeProcessingAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "knowledge_processing_attempt_id")
    private Long id;

    @Column(name = "knowledge_processing_job_id", nullable = false)
    private Long knowledgeProcessingJobId;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "request_id", nullable = false, length = 36)
    @Convert(converter = UuidStringConverter.class)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeProcessingAttemptStatus status;

    @Column(name = "terminal_fingerprint", length = 64)
    private String terminalFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_status", length = 20)
    private KnowledgeProcessingCallback.Status terminalStatus;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "chunk_profile", length = 50)
    private String chunkProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 100)
    private KnowledgeProcessingCallback.FailureCode failureCode;

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

    protected KnowledgeProcessingAttempt() {
    }

    private KnowledgeProcessingAttempt(
            Long knowledgeProcessingJobId,
            int attempt,
            UUID requestId,
            LocalDateTime now
    ) {
        if (knowledgeProcessingJobId == null || knowledgeProcessingJobId <= 0) {
            throw new IllegalArgumentException("Knowledge 처리 Job ID는 양수여야 합니다.");
        }
        if (attempt < 1 || attempt > 3) {
            throw new IllegalArgumentException("attempt는 1에서 3 사이여야 합니다.");
        }
        this.knowledgeProcessingJobId = knowledgeProcessingJobId;
        this.attempt = attempt;
        this.requestId = Objects.requireNonNull(requestId);
        this.status = KnowledgeProcessingAttemptStatus.CREATED;
        this.retryable = false;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static KnowledgeProcessingAttempt create(
            Long knowledgeProcessingJobId,
            int attempt,
            UUID requestId,
            LocalDateTime now
    ) {
        return new KnowledgeProcessingAttempt(knowledgeProcessingJobId, attempt, requestId, now);
    }

    public void markSubmitted(LocalDateTime now) {
        status = KnowledgeProcessingAttemptStatus.SUBMITTED;
        submittedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void markAccepted(LocalDateTime now) {
        if (status != KnowledgeProcessingAttemptStatus.TERMINAL) {
            status = KnowledgeProcessingAttemptStatus.ACCEPTED;
        }
        acceptedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void markSuperseded(LocalDateTime now) {
        if (status != KnowledgeProcessingAttemptStatus.TERMINAL) {
            status = KnowledgeProcessingAttemptStatus.SUPERSEDED;
            updatedAt = Objects.requireNonNull(now);
        }
    }

    public void markSubmissionFailed(LocalDateTime now) {
        if (status != KnowledgeProcessingAttemptStatus.TERMINAL) {
            status = KnowledgeProcessingAttemptStatus.SUBMISSION_FAILED;
            updatedAt = Objects.requireNonNull(now);
        }
    }

    public void applyTerminal(
            KnowledgeProcessingCallback callback,
            String fingerprint,
            LocalDateTime now
    ) {
        status = KnowledgeProcessingAttemptStatus.TERMINAL;
        terminalFingerprint = requireFingerprint(fingerprint);
        terminalStatus = Objects.requireNonNull(callback).status();
        chunkCount = callback.chunkCount();
        chunkProfile = callback.chunkProfile();
        failureCode = callback.failureCode();
        retryable = Boolean.TRUE.equals(callback.retryable());
        callbackReceivedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public KnowledgeProcessingCallback terminalCallback(long processingId, long knowledgeVersionId) {
        if (terminalStatus == null) {
            return null;
        }
        return new KnowledgeProcessingCallback(
                processingId,
                knowledgeVersionId,
                terminalStatus,
                chunkCount,
                chunkProfile,
                failureCode,
                terminalStatus == KnowledgeProcessingCallback.Status.FAILED ? retryable : null
        );
    }

    private static String requireFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("terminal fingerprint 형식이 올바르지 않습니다.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getKnowledgeProcessingJobId() {
        return knowledgeProcessingJobId;
    }

    public int getAttempt() {
        return attempt;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public KnowledgeProcessingAttemptStatus getStatus() {
        return status;
    }

    public String getTerminalFingerprint() {
        return terminalFingerprint;
    }
}
