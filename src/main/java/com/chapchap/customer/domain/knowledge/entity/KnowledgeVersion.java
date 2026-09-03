package com.chapchap.customer.domain.knowledge.entity;

import com.chapchap.customer.global.error.custom.knowledge.KnowledgeVersionStateException;
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
        name = "knowledge_versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_knowledge_versions_document_version",
                        columnNames = {"knowledge_document_id", "version"}
                ),
                @UniqueConstraint(
                        name = "uk_knowledge_versions_object_key",
                        columnNames = "object_key"
                )
        }
)
public class KnowledgeVersion {
    public static final String CHUNK_PROFILE = "HYBRID_POLICY_V1";
    public static final int MAX_PROCESSING_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "knowledge_version_id")
    private Long id;

    @Column(name = "knowledge_document_id", nullable = false)
    private Long knowledgeDocumentId;

    @Column(nullable = false, length = 30)
    private String version;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "chunk_profile", nullable = false, length = 50)
    private String chunkProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private KnowledgeProcessingStatus processingStatus;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "is_retryable", nullable = false)
    private boolean retryable;

    @Column(name = "processing_attempt_count", nullable = false)
    private int processingAttemptCount;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected KnowledgeVersion() {
    }

    private KnowledgeVersion(
            Long knowledgeDocumentId,
            String version,
            String objectKey,
            String originalFilename,
            String contentType,
            long fileSize,
            LocalDateTime effectiveFrom,
            Long uploadedByUserId,
            LocalDateTime now
    ) {
        this.knowledgeDocumentId = knowledgeDocumentId;
        this.version = version;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.chunkProfile = CHUNK_PROFILE;
        this.processingStatus = KnowledgeProcessingStatus.UPLOADED;
        this.active = false;
        this.retryable = false;
        this.processingAttemptCount = 0;
        this.effectiveFrom = effectiveFrom;
        this.uploadedByUserId = uploadedByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static KnowledgeVersion uploaded(
            Long knowledgeDocumentId,
            String version,
            String objectKey,
            String originalFilename,
            String contentType,
            long fileSize,
            LocalDateTime effectiveFrom,
            Long uploadedByUserId,
            LocalDateTime now
    ) {
        return new KnowledgeVersion(
                knowledgeDocumentId,
                version,
                objectKey,
                originalFilename,
                contentType,
                fileSize,
                effectiveFrom,
                uploadedByUserId,
                now
        );
    }

    public boolean canActivateAt(LocalDateTime now) {
        return !active
                && processingStatus == KnowledgeProcessingStatus.READY
                && !effectiveFrom.isAfter(now);
    }

    public void activate(LocalDateTime now) {
        if (!canActivateAt(now)) {
            throw new KnowledgeVersionStateException("시행 가능한 READY Knowledge Version만 활성화할 수 있습니다.");
        }

        active = true;
        activatedAt = now;
        updatedAt = now;
    }

    public void deactivate(LocalDateTime now) {
        active = false;
        updatedAt = now;
    }

    public void startProcessing(LocalDateTime now) {
        if (processingStatus == KnowledgeProcessingStatus.READY
                || processingStatus == KnowledgeProcessingStatus.FAILED
                || processingAttemptCount >= MAX_PROCESSING_ATTEMPTS) {
            throw new KnowledgeVersionStateException("처리할 수 없는 Knowledge Version 상태입니다.");
        }

        processingStatus = KnowledgeProcessingStatus.PROCESSING;
        processingAttemptCount++;
        failureCode = null;
        retryable = false;
        updatedAt = now;
    }

    public void completeProcessing(LocalDateTime now) {
        if (processingStatus != KnowledgeProcessingStatus.PROCESSING) {
            throw new KnowledgeVersionStateException("PROCESSING 상태의 Knowledge Version만 완료할 수 있습니다.");
        }

        processingStatus = KnowledgeProcessingStatus.READY;
        failureCode = null;
        retryable = false;
        processedAt = now;
        updatedAt = now;
    }

    public void failProcessing(String failureCode, boolean retryable, LocalDateTime now) {
        processingStatus = KnowledgeProcessingStatus.FAILED;
        this.failureCode = failureCode;
        this.retryable = retryable;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getKnowledgeDocumentId() {
        return knowledgeDocumentId;
    }

    public String getVersion() {
        return version;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getChunkProfile() {
        return chunkProfile;
    }

    public KnowledgeProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public boolean isActive() {
        return active;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public int getProcessingAttemptCount() {
        return processingAttemptCount;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
