package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultationSummaryCallbackStateMachineTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ConsultationSummaryCallbackHeaders HEADERS =
            new ConsultationSummaryCallbackHeaders(REQUEST_ID, 7001L);
    private final ConsultationSummaryCallbackStateMachine stateMachine =
            new ConsultationSummaryCallbackStateMachine();

    @Test
    void appliesFirstCompletedAndFailedCallbacksWithoutLifecycleMutation() {
        ConsultationSummaryJobSnapshot completedSnapshot = snapshot(null);
        ConsultationSummaryJobSnapshot failedSnapshot = snapshot(null);

        assertThat(stateMachine.decide(completedSnapshot, HEADERS, completed("요약")))
                .isEqualTo(ConsultationSummaryCallbackOutcome.APPLIED_COMPLETED);
        assertThat(stateMachine.decide(failedSnapshot, HEADERS, failed(true)))
                .isEqualTo(ConsultationSummaryCallbackOutcome.APPLIED_FAILED);
        assertThat(completedSnapshot.consultationStatus()).isEqualTo(ConsultationStatus.CLOSED);
        assertThat(failedSnapshot.consultationStatus()).isEqualTo(ConsultationStatus.CLOSED);
    }

    @Test
    void snapshotRejectsAnyConsultationThatIsNotClosed() {
        assertThatThrownBy(() -> new ConsultationSummaryJobSnapshot(
                REQUEST_ID, 7001L, 501L, ConsultationStatus.IN_PROGRESS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void snapshotRejectsTerminalCallbackFromAnotherJob() {
        ConsultationSummaryCallback wrongJob = new ConsultationSummaryCallback(
                7002L, 501L, ConsultationSummaryCallback.Status.COMPLETED,
                "요약", null, null);

        assertThatThrownBy(() -> new ConsultationSummaryJobSnapshot(
                REQUEST_ID, 7001L, 501L, ConsultationStatus.CLOSED, wrongJob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity");
    }
    @Test
    void ignoresExactDuplicateTerminalCallback() {
        ConsultationSummaryCallback completed = completed("요약");

        assertThat(stateMachine.decide(snapshot(completed), HEADERS, completed))
                .isEqualTo(ConsultationSummaryCallbackOutcome.IGNORED_DUPLICATE);
    }

    @Test
    void rejectsConflictingTerminalCallback() {
        assertThat(stateMachine.decide(snapshot(completed("첫 요약")), HEADERS, completed("다른 요약")))
                .isEqualTo(ConsultationSummaryCallbackOutcome.CONFLICT);
    }

    @Test
    void ignoresUnknownOrOlderSummaryJob() {
        assertThat(stateMachine.decide(null, HEADERS, completed("요약")))
                .isEqualTo(ConsultationSummaryCallbackOutcome.IGNORED_STALE);
        ConsultationSummaryJobSnapshot newer = new ConsultationSummaryJobSnapshot(
                REQUEST_ID, 8001L, 501L, ConsultationStatus.CLOSED, null);
        assertThat(stateMachine.decide(newer, HEADERS, completed("요약")))
                .isEqualTo(ConsultationSummaryCallbackOutcome.IGNORED_STALE);
    }

    @Test
    void rejectsRequestAndConsultationIdentityMismatch() {
        ConsultationSummaryCallback wrongConsultation = new ConsultationSummaryCallback(
                7001L, 502L, ConsultationSummaryCallback.Status.COMPLETED,
                "요약", null, null);
        ConsultationSummaryCallbackHeaders wrongRequest = new ConsultationSummaryCallbackHeaders(
                UUID.fromString("22222222-2222-4222-8222-222222222222"), 7001L);

        assertThat(stateMachine.decide(snapshot(null), HEADERS, wrongConsultation))
                .isEqualTo(ConsultationSummaryCallbackOutcome.CONFLICT);
        assertThat(stateMachine.decide(snapshot(null), wrongRequest, completed("요약")))
                .isEqualTo(ConsultationSummaryCallbackOutcome.CONFLICT);
    }

    @Test
    void consumerParsesVerifiedPayloadAndRejectsAtomicStateConflict() {
        ConsultationSummaryCallbackParser parser = new ConsultationSummaryCallbackParser(new ObjectMapper());
        ConsultationSummaryCallbackConsumer duplicate = new ConsultationSummaryCallbackConsumer(
                parser,
                (headers, callback) -> ConsultationSummaryCallbackOutcome.IGNORED_DUPLICATE
        );
        ConsultationSummaryCallbackConsumer conflict = new ConsultationSummaryCallbackConsumer(
                parser,
                (headers, callback) -> ConsultationSummaryCallbackOutcome.CONFLICT
        );

        assertThat(duplicate.consumeVerified(HEADERS, completedJson()))
                .isEqualTo(ConsultationSummaryCallbackOutcome.IGNORED_DUPLICATE);
        assertThatThrownBy(() -> conflict.consumeVerified(HEADERS, completedJson()))
                .isExactlyInstanceOf(ConsultationSummaryCallbackException.class)
                .satisfies(error -> assertThat(((ConsultationSummaryCallbackException) error).reason())
                        .isEqualTo(ConsultationSummaryCallbackException.Reason.STATE_CONFLICT));
    }

    private ConsultationSummaryJobSnapshot snapshot(ConsultationSummaryCallback terminal) {
        return new ConsultationSummaryJobSnapshot(
                REQUEST_ID, 7001L, 501L, ConsultationStatus.CLOSED, terminal);
    }

    private ConsultationSummaryCallback completed(String summary) {
        return new ConsultationSummaryCallback(
                7001L, 501L, ConsultationSummaryCallback.Status.COMPLETED,
                summary, null, null);
    }

    private ConsultationSummaryCallback failed(boolean retryable) {
        return new ConsultationSummaryCallback(
                7001L, 501L, ConsultationSummaryCallback.Status.FAILED,
                null, ConsultationSummaryCallback.FailureCode.LLM_UNAVAILABLE, retryable);
    }

    private String completedJson() {
        return """
                {"schemaVersion":"1.0","summaryJobId":7001,"consultationId":501,
                "status":"COMPLETED","summary":"요약"}
                """;
    }
}