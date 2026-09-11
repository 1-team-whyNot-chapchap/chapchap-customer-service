package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;

import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingAttemptStatus;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;
import com.chapchap.customer.domain.knowledge.service.processing.async.KnowledgeProcessingCallbackFingerprint;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeProcessingAttemptTest {
    @Test
    void storesOnlyMinimumTerminalFieldsAndRestoresCallbackForDuplicateDecision() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 16, 0);
        KnowledgeProcessingAttempt attempt = KnowledgeProcessingAttempt.create(
                1L, 1, UUID.randomUUID(), now);
        KnowledgeProcessingCallback callback = new KnowledgeProcessingCallback(
                8001L,
                101L,
                KnowledgeProcessingCallback.Status.COMPLETED,
                24,
                "HYBRID_POLICY_V1",
                null,
                null
        );

        attempt.applyTerminal(
                callback,
                KnowledgeProcessingCallbackFingerprint.create(callback),
                now.plusSeconds(1));

        assertThat(attempt.getStatus()).isEqualTo(KnowledgeProcessingAttemptStatus.TERMINAL);
        assertThat(attempt.terminalCallback(8001L, 101L)).isEqualTo(callback);
        assertThat(attempt.getTerminalFingerprint()).hasSize(64);
    }
}
