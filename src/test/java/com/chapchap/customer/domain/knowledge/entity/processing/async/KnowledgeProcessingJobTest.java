package com.chapchap.customer.domain.knowledge.entity.processing.async;

import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingJobStatus;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeProcessingJobTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 16, 0);

    @Test
    void keepsOneProcessingIdAcrossAttemptsAndResetsCurrentTerminalState() {
        KnowledgeProcessingJob job = KnowledgeProcessingJob.create(101L, "HYBRID_POLICY_V1", NOW);
        job.bindProcessingId(8001L, NOW);
        job.applyFailed("a".repeat(64),
                KnowledgeProcessingCallback.FailureCode.VECTOR_STORE_UNAVAILABLE,
                true,
                NOW);

        job.beginAttempt(2, NOW.plusSeconds(1));
        job.bindProcessingId(8001L, NOW.plusSeconds(2));

        assertThat(job.getProcessingId()).isEqualTo(8001L);
        assertThat(job.getCurrentAttempt()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(KnowledgeProcessingJobStatus.ACCEPTED);
        assertThat(job.getTerminalFingerprint()).isNull();
        assertThat(job.getFailureCode()).isNull();
    }

    @Test
    void rejectsChangingProcessingIdOrSkippingAttempt() {
        KnowledgeProcessingJob job = KnowledgeProcessingJob.create(101L, "HYBRID_POLICY_V1", NOW);
        job.bindProcessingId(8001L, NOW);

        assertThatThrownBy(() -> job.bindProcessingId(8002L, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.beginAttempt(3, NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
