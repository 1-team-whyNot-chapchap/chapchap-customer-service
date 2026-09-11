package com.chapchap.customer.domain.consultation.entity;

import com.chapchap.customer.domain.consultation.constant.ConsultationSenderType;

import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "consultation_message_sources",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_consultation_message_sources_chunk",
                columnNames = {"message_id", "source_chunk_id"}
        )
)
public class ConsultationMessageSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_source_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ConsultationMessage message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_version_id", nullable = false)
    private KnowledgeVersion knowledgeVersion;

    @Column(name = "source_chunk_id", nullable = false, length = 255)
    private String sourceChunkId;

    @Column(name = "retrieval_rank", nullable = false)
    private int retrievalRank;

    @Column(name = "similarity_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal similarityScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ConsultationMessageSource() {
    }

    private ConsultationMessageSource(
            ConsultationMessage message,
            KnowledgeVersion knowledgeVersion,
            String sourceChunkId,
            int retrievalRank,
            double similarityScore,
            LocalDateTime now
    ) {
        if (message == null || message.getSenderType() != ConsultationSenderType.AI) {
            throw new IllegalArgumentException("AI 메시지에만 Knowledge 근거를 연결할 수 있습니다.");
        }
        if (sourceChunkId == null || sourceChunkId.isBlank() || sourceChunkId.length() > 255) {
            throw new IllegalArgumentException("Knowledge Chunk ID가 올바르지 않습니다.");
        }
        if (knowledgeVersion == null
                || knowledgeVersion.getProcessingStatus() != KnowledgeProcessingStatus.READY) {
            throw new IllegalArgumentException("READY Knowledge Version만 근거로 연결할 수 있습니다.");
        }
        if (retrievalRank < 1 || retrievalRank > 5
                || !Double.isFinite(similarityScore)
                || similarityScore < 0 || similarityScore > 1) {
            throw new IllegalArgumentException("Knowledge 검색 근거 값이 올바르지 않습니다.");
        }
        this.message = message;
        this.knowledgeVersion = knowledgeVersion;
        this.sourceChunkId = sourceChunkId;
        this.retrievalRank = retrievalRank;
        this.similarityScore = BigDecimal.valueOf(similarityScore).setScale(5, java.math.RoundingMode.HALF_UP);
        this.createdAt = Objects.requireNonNull(now);
    }

    public static ConsultationMessageSource create(
            ConsultationMessage message,
            KnowledgeVersion knowledgeVersion,
            String sourceChunkId,
            int retrievalRank,
            double similarityScore,
            LocalDateTime now
    ) {
        return new ConsultationMessageSource(
                message, knowledgeVersion, sourceChunkId, retrievalRank, similarityScore, now);
    }

    public Long getId() {
        return id;
    }

    public Long getMessageId() {
        return message.getId();
    }

    public Long getKnowledgeVersionId() {
        return knowledgeVersion.getId();
    }

    public String getSourceChunkId() {
        return sourceChunkId;
    }

    public int getRetrievalRank() {
        return retrievalRank;
    }

    public BigDecimal getSimilarityScore() {
        return similarityScore;
    }
}
