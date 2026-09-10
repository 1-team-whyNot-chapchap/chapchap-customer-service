package com.chapchap.customer.domain.consultation.entity.summary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "consultation_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_consultation_summaries_consultation",
                columnNames = "consultation_id"
        )
)
public class ConsultationSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long id;

    @Column(name = "consultation_id", nullable = false)
    private Long consultationId;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "final_summary", columnDefinition = "TEXT")
    private String finalSummary;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ConsultationSummary() {
    }

    private ConsultationSummary(Long consultationId, String aiSummary, LocalDateTime now) {
        if (consultationId == null || consultationId <= 0) {
            throw new IllegalArgumentException("consultationId는 양수여야 합니다.");
        }
        this.consultationId = consultationId;
        this.aiSummary = requireSummary(aiSummary);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static ConsultationSummary fromAi(
            Long consultationId,
            String aiSummary,
            LocalDateTime now
    ) {
        return new ConsultationSummary(consultationId, aiSummary, now);
    }

    private String requireSummary(String value) {
        if (value == null || value.isBlank() || value.length() > 10_000) {
            throw new IllegalArgumentException("AI 요약 내용이 올바르지 않습니다.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getConsultationId() {
        return consultationId;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public String getFinalSummary() {
        return finalSummary;
    }
}
