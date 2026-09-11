package com.chapchap.customer.domain.consultation.entity.summary;

import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryJobStatus;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultationSummaryJobTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 16, 0);

    @Test
    void terminalCallbackWinsOverLateSubmissionFailure() {
        ConsultationSummaryJob job = ConsultationSummaryJob.create(501L, UUID.randomUUID(), NOW);
        job.applyCompleted("a".repeat(64), NOW.plusSeconds(1));

        job.markSubmissionFailed("TIMEOUT", true, NOW.plusSeconds(2));

        assertThat(job.getStatus()).isEqualTo(ConsultationSummaryJobStatus.COMPLETED);
        assertThat(job.getFailureCode()).isNull();
        assertThat(job.isRetryable()).isFalse();
    }

    @Test
    void storesFailureMetadataWithoutSummaryContent() {
        ConsultationSummaryJob job = ConsultationSummaryJob.create(501L, UUID.randomUUID(), NOW);

        job.applyFailed(
                "b".repeat(64),
                ConsultationSummaryCallback.FailureCode.LLM_UNAVAILABLE,
                true,
                NOW.plusSeconds(1));

        assertThat(job.getStatus()).isEqualTo(ConsultationSummaryJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(job.isRetryable()).isTrue();
    }
}
