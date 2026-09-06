package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticEvent;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticOutcome;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticPublisher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultationSummaryCallbackDiagnosticsTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ConsultationSummaryCallbackHeaders HEADERS =
            new ConsultationSummaryCallbackHeaders(REQUEST_ID, 7001);

    @Test
    void correlatesCompletedAndFailedCallbacksWithoutSummaryContent() {
        List<CustomerAiDiagnosticEvent> completedEvents = new ArrayList<>();
        ConsultationSummaryCallbackConsumer completed = consumer(
                ConsultationSummaryCallbackOutcome.APPLIED_COMPLETED, completedEvents::add);
        assertThat(completed.consumeVerified(HEADERS, completedBody()))
                .isEqualTo(ConsultationSummaryCallbackOutcome.APPLIED_COMPLETED);
        assertThat(completedEvents).singleElement().satisfies(event -> {
            assertThat(event.requestId()).isEqualTo(REQUEST_ID);
            assertThat(event.consultationId()).isEqualTo(501);
            assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.COMPLETED);
            assertThat(event.processingId()).isNull();
        });

        List<CustomerAiDiagnosticEvent> failedEvents = new ArrayList<>();
        ConsultationSummaryCallbackConsumer failed = consumer(
                ConsultationSummaryCallbackOutcome.APPLIED_FAILED, failedEvents::add);
        assertThat(failed.consumeVerified(HEADERS, failedBody()))
                .isEqualTo(ConsultationSummaryCallbackOutcome.APPLIED_FAILED);
        assertThat(failedEvents).singleElement().satisfies(event -> {
            assertThat(event.failureCode()).isEqualTo(CustomerAiDiagnosticFailureCode.LLM_UNAVAILABLE);
            assertThat(event.retryable()).isTrue();
        });
    }

    @Test
    void distinguishesStaleCallbackAndStateConflict() {
        List<CustomerAiDiagnosticEvent> staleEvents = new ArrayList<>();
        ConsultationSummaryCallbackConsumer stale = consumer(
                ConsultationSummaryCallbackOutcome.IGNORED_STALE, staleEvents::add);
        assertThat(stale.consumeVerified(HEADERS, completedBody()))
                .isEqualTo(ConsultationSummaryCallbackOutcome.IGNORED_STALE);
        assertThat(staleEvents).singleElement().satisfies(event ->
                assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.IGNORED_STALE));

        List<CustomerAiDiagnosticEvent> conflictEvents = new ArrayList<>();
        ConsultationSummaryCallbackConsumer conflict = consumer(
                ConsultationSummaryCallbackOutcome.CONFLICT, conflictEvents::add);
        assertThatThrownBy(() -> conflict.consumeVerified(HEADERS, completedBody()))
                .isInstanceOf(ConsultationSummaryCallbackException.class);
        assertThat(conflictEvents).singleElement().satisfies(event ->
                assertThat(event.failureCode()).isEqualTo(CustomerAiDiagnosticFailureCode.STATE_CONFLICT));
    }

    @Test
    void failingSinkDoesNotChangeCallbackOutcome() {
        ConsultationSummaryCallbackConsumer consumer = consumer(
                ConsultationSummaryCallbackOutcome.IGNORED_DUPLICATE,
                event -> {
                    throw new IllegalStateException("sink unavailable");
                }
        );

        assertThat(consumer.consumeVerified(HEADERS, completedBody()))
                .isEqualTo(ConsultationSummaryCallbackOutcome.IGNORED_DUPLICATE);
    }

    private ConsultationSummaryCallbackConsumer consumer(
            ConsultationSummaryCallbackOutcome outcome,
            com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticSink sink
    ) {
        return new ConsultationSummaryCallbackConsumer(
                new ConsultationSummaryCallbackParser(new ObjectMapper()),
                (headers, callback) -> outcome,
                new CustomerAiDiagnosticPublisher(sink, () -> "trace-summary")
        );
    }

    private String completedBody() {
        return """
                {"schemaVersion":"1.0","summaryJobId":7001,"consultationId":501,
                "status":"COMPLETED","summary":"sensitive summary is never emitted"}
                """;
    }

    private String failedBody() {
        return """
                {"schemaVersion":"1.0","summaryJobId":7001,"consultationId":501,
                "status":"FAILED","failureCode":"LLM_UNAVAILABLE","retryable":true}
                """;
    }
}
