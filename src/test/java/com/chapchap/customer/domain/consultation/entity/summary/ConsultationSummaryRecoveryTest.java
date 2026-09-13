package com.chapchap.customer.domain.consultation.entity.summary;

import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConsultationSummaryRecoveryTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 13, 16, 0);

    @Test void pendingSurvivesRestartAndLeasePreventsParallelSubmission() {
        var job = ConsultationSummaryJob.create(1L, UUID.randomUUID(), now);
        job.rememberCutoff(7);
        assertThat(job.canRecover(now)).isTrue();
        job.markSubmitted(now);
        assertThat(job.canRecover(now.plusSeconds(119))).isFalse();
        assertThat(job.canRecover(now.plusSeconds(120))).isTrue();
        assertThatThrownBy(() -> job.rememberCutoff(9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void failureRetryChangesCallbackIdentityAndAutomaticAttemptsAreBounded() {
        var oldId = UUID.randomUUID();
        var job = ConsultationSummaryJob.create(1L, oldId, now);
        job.rememberCutoff(7);
        job.markSubmitted(now);
        job.applyFailed("a".repeat(64), ConsultationSummaryCallback.FailureCode.LLM_UNAVAILABLE, true, now);
        assertThat(job.canRecover(now.plusSeconds(29))).isFalse();
        assertThat(job.canRecover(now.plusSeconds(30))).isTrue();
        job.retry(now.plusSeconds(30));
        assertThat(job.getRequestId()).isNotEqualTo(oldId);
        assertThat(job.getTerminalFingerprint()).isNull();
        job.markSubmitted(now.plusSeconds(30));
        job.markSubmissionFailed("TIMEOUT", true, now.plusSeconds(30));
        job.markSubmitted(now.plusSeconds(60));
        job.markSubmissionFailed("TIMEOUT", true, now.plusSeconds(60));
        assertThat(job.canRecover(now.plusSeconds(200))).isFalse();
        job.retry(now.plusSeconds(200));
        assertThat(job.canRecover(now.plusSeconds(200))).isTrue();
    }

    @Test void completedAndLegacyJobsAreNeverReplayed() {
        var job = ConsultationSummaryJob.create(1L, UUID.randomUUID(), now);
        assertThat(job.canRecover(now.plusHours(1))).isFalse();
        job.rememberCutoff(7);
        job.applyCompleted("b".repeat(64), now);
        assertThat(job.canRecover(now.plusHours(1))).isFalse();
        assertThatThrownBy(() -> job.retry(now.plusHours(1))).isInstanceOf(IllegalStateException.class);
    }
}
