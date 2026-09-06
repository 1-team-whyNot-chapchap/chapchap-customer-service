package com.chapchap.customer.domain.knowledge.processing.async;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeProcessingCallbackStateMachineTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final KnowledgeProcessingCallbackHeaders HEADERS =
            new KnowledgeProcessingCallbackHeaders(REQUEST_ID, 8001L);
    private final KnowledgeProcessingCallbackStateMachine stateMachine =
            new KnowledgeProcessingCallbackStateMachine();

    @Test
    void appliesFirstCompletedCallback() {
        assertThat(stateMachine.decide(snapshot(null), HEADERS, completed(3)))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED);
    }

    @Test
    void appliesFirstFailedCallback() {
        assertThat(stateMachine.decide(snapshot(null), HEADERS, failed(true)))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.APPLIED_FAILED);
    }

    @Test
    void ignoresExactDuplicateTerminalCallback() {
        KnowledgeProcessingCallback completed = completed(3);

        assertThat(stateMachine.decide(snapshot(completed), HEADERS, completed))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE);
    }

    @Test
    void rejectsConflictingTerminalCallback() {
        assertThat(stateMachine.decide(snapshot(completed(3)), HEADERS, completed(4)))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.CONFLICT);
    }

    @Test
    void ignoresUnknownOrOlderProcessingId() {
        assertThat(stateMachine.decide(null, HEADERS, completed(3)))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.IGNORED_STALE);
        KnowledgeProcessingJobSnapshot newer = new KnowledgeProcessingJobSnapshot(
                REQUEST_ID, 9001L, 101L, 2, "HYBRID_POLICY_V1", null);
        assertThat(stateMachine.decide(newer, HEADERS, completed(3)))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.IGNORED_STALE);
    }

    @Test
    void rejectsRequestVersionAndProfileMismatchForCurrentJob() {
        KnowledgeProcessingCallback wrongVersion = new KnowledgeProcessingCallback(
                8001L, 102L, KnowledgeProcessingCallback.Status.COMPLETED,
                3, "HYBRID_POLICY_V1", null, null);
        KnowledgeProcessingCallback wrongProfile = new KnowledgeProcessingCallback(
                8001L, 101L, KnowledgeProcessingCallback.Status.COMPLETED,
                3, "OTHER", null, null);
        KnowledgeProcessingCallbackHeaders wrongRequest = new KnowledgeProcessingCallbackHeaders(
                UUID.fromString("22222222-2222-4222-8222-222222222222"), 8001L);

        assertThat(stateMachine.decide(snapshot(null), HEADERS, wrongVersion))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.CONFLICT);
        assertThat(stateMachine.decide(snapshot(null), HEADERS, wrongProfile))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.CONFLICT);
        assertThat(stateMachine.decide(snapshot(null), wrongRequest, completed(3)))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.CONFLICT);
    }

    @Test
    void consumerParsesVerifiedPayloadAndRejectsAtomicStateConflict() {
        KnowledgeProcessingCallbackParser parser = new KnowledgeProcessingCallbackParser(new ObjectMapper());
        KnowledgeProcessingCallbackConsumer accepted = new KnowledgeProcessingCallbackConsumer(
                parser,
                (headers, callback) -> KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE
        );
        KnowledgeProcessingCallbackConsumer conflict = new KnowledgeProcessingCallbackConsumer(
                parser,
                (headers, callback) -> KnowledgeProcessingCallbackOutcome.CONFLICT
        );

        assertThat(accepted.consumeVerified(HEADERS, completedJson()))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE);
        assertThatThrownBy(() -> conflict.consumeVerified(HEADERS, completedJson()))
                .isExactlyInstanceOf(KnowledgeProcessingCallbackException.class)
                .satisfies(error -> assertThat(((KnowledgeProcessingCallbackException) error).reason())
                        .isEqualTo(KnowledgeProcessingCallbackException.Reason.STATE_CONFLICT));
    }

    private KnowledgeProcessingJobSnapshot snapshot(KnowledgeProcessingCallback terminal) {
        return new KnowledgeProcessingJobSnapshot(
                REQUEST_ID, 8001L, 101L, 1, "HYBRID_POLICY_V1", terminal);
    }

    private KnowledgeProcessingCallback completed(int chunkCount) {
        return new KnowledgeProcessingCallback(
                8001L, 101L, KnowledgeProcessingCallback.Status.COMPLETED,
                chunkCount, "HYBRID_POLICY_V1", null, null);
    }

    private KnowledgeProcessingCallback failed(boolean retryable) {
        return new KnowledgeProcessingCallback(
                8001L, 101L, KnowledgeProcessingCallback.Status.FAILED,
                null, null, KnowledgeProcessingCallback.FailureCode.EMBEDDING_UNAVAILABLE, retryable);
    }

    private String completedJson() {
        return """
                {"schemaVersion":"1.0","processingId":8001,"knowledgeVersionId":101,
                "status":"COMPLETED","chunkCount":3,"chunkProfile":"HYBRID_POLICY_V1"}
                """;
    }
}