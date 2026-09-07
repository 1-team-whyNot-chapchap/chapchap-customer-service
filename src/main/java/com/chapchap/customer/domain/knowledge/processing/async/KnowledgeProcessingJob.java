package com.chapchap.customer.domain.knowledge.processing.async;

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
import java.util.Objects;

@Entity
@Table(
        name = "knowledge_processing_jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_knowledge_processing_jobs_version",
                        columnNames = "knowledge_version_id"
                ),
                @UniqueConstraint(
                        name = "uk_knowledge_processing_jobs_processing",
                        columnNames = "processing_id"
                )
        }
)
public class KnowledgeProcessingJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "knowledge_processing_job_id")
    private Long id;

    @Column(name = "knowledge_version_id", nullable = false)
    private Long knowledgeVersionId;

    @Column(name = "processing_id")
    private Long processingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeProcessingJobStatus status;

    @Column(name = "current_attempt", nullable = false)
    private int currentAttempt;

    @Column(name = "chunk_profile", nullable = false, length = 50)
    private String chunkProfile;

    @Column(name = "terminal_fingerprint", length = 64)
    private String terminalFingerprint;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "is_retryable", nullable = false)
    private boolean retryable;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "callback_received_at")
    private LocalDateTime callbackReceivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected KnowledgeProcessingJob() {
    }

    private KnowledgeProcessingJob(
            Long knowledgeVersionId,
            String chunkProfile,
            LocalDateTime now
    ) {
        this.knowledgeVersionId = requirePositive(knowledgeVersionId, "knowledgeVersionId");
        this.chunkProfile = requireChunkProfile(chunkProfile);
        this.status = KnowledgeProcessingJobStatus.PENDING;
        this.currentAttempt = 1;
        this.retryable = false;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static KnowledgeProcessingJob create(
            Long knowledgeVersionId,
            String chunkProfile,
            LocalDateTime now
    ) {
        return new KnowledgeProcessingJob(knowledgeVersionId, chunkProfile, now);
    }

    public void beginAttempt(int attempt, LocalDateTime now) {
        if (attempt < 1 || attempt > 3 || attempt != currentAttempt + 1) {
            throw new IllegalStateException("Knowledge 처리 attempt 순서가 올바르지 않습니다.");
        }
        currentAttempt = attempt;
        status = KnowledgeProcessingJobStatus.PENDING;
        terminalFingerprint = null;
        failureCode = null;
        retryable = false;
        chunkCount = null;
        callbackReceivedAt = null;
        updatedAt = Objects.requireNonNull(now);
    }

    public void bindProcessingId(long acceptedProcessingId, LocalDateTime now) {
        if (acceptedProcessingId <= 0) {
            throw new IllegalArgumentException("processingId는 양수여야 합니다.");
        }
        if (processingId != null && processingId != acceptedProcessingId) {
            throw new IllegalStateException("Knowledge 처리 작업의 processingId가 충돌합니다.");
        }
        processingId = acceptedProcessingId;
        if (status == KnowledgeProcessingJobStatus.PENDING) {
            status = KnowledgeProcessingJobStatus.ACCEPTED;
        }
        updatedAt = Objects.requireNonNull(now);
    }

    public void applyCompleted(
            String fingerprint,
            int completedChunkCount,
            LocalDateTime now
    ) {
        terminalFingerprint = requireFingerprint(fingerprint);
        if (completedChunkCount <= 0) {
            throw new IllegalArgumentException("chunkCount는 양수여야 합니다.");
        }
        status = KnowledgeProcessingJobStatus.COMPLETED;
        failureCode = null;
        retryable = false;
        chunkCount = completedChunkCount;
        callbackReceivedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void applyFailed(
            String fingerprint,
            KnowledgeProcessingCallback.FailureCode callbackFailureCode,
            boolean callbackRetryable,
            LocalDateTime now
    ) {
        terminalFingerprint = requireFingerprint(fingerprint);
        status = KnowledgeProcessingJobStatus.FAILED;
        failureCode = Objects.requireNonNull(callbackFailureCode).name();
        retryable = callbackRetryable;
        chunkCount = null;
        callbackReceivedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void markSubmissionFailed(String code, boolean submissionRetryable, LocalDateTime now) {
        if (code == null || code.isBlank() || code.length() > 100) {
            throw new IllegalArgumentException("제출 실패 코드가 올바르지 않습니다.");
        }
        status = KnowledgeProcessingJobStatus.FAILED;
        terminalFingerprint = null;
        failureCode = code;
        retryable = submissionRetryable;
        chunkCount = null;
        callbackReceivedAt = null;
        updatedAt = Objects.requireNonNull(now);
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + "는 양수여야 합니다.");
        }
        return value;
    }

    private static String requireChunkProfile(String value) {
        if (!CustomerAiKnowledgeJobCommand.SUPPORTED_CHUNK_PROFILE.equals(value)) {
            throw new IllegalArgumentException("지원하지 않는 chunkProfile입니다.");
        }
        return value;
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

    public Long getKnowledgeVersionId() {
        return knowledgeVersionId;
    }

    public Long getProcessingId() {
        return processingId;
    }

    public KnowledgeProcessingJobStatus getStatus() {
        return status;
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    public String getChunkProfile() {
        return chunkProfile;
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

    public Integer getChunkCount() {
        return chunkCount;
    }

    public LocalDateTime getCallbackReceivedAt() {
        return callbackReceivedAt;
    }
}
