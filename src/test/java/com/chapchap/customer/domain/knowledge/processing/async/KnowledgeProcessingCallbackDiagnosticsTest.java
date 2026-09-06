package com.chapchap.customer.domain.knowledge.processing.async;

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

class KnowledgeProcessingCallbackDiagnosticsTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final KnowledgeProcessingCallbackHeaders HEADERS =
            new KnowledgeProcessingCallbackHeaders(REQUEST_ID, 8001);

    @Test
    void correlatesCompletedCallbackWithRequestAndJobIdentifiers() {
        List<CustomerAiDiagnosticEvent> events = new ArrayList<>();
        KnowledgeProcessingCallbackConsumer consumer = consumer(
                KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED, events::add);

        assertThat(consumer.consumeVerified(HEADERS, completedBody()))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.requestId()).isEqualTo(REQUEST_ID);
            assertThat(event.traceId()).isEqualTo("trace-knowledge");
            assertThat(event.knowledgeVersionId()).isEqualTo(101);
            assertThat(event.processingId()).isEqualTo(8001);
            assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.COMPLETED);
            assertThat(event.chunkCount()).isEqualTo(3);
        });
    }

    @Test
    void recordsProviderFailureWithoutRawCallbackBody() {
        List<CustomerAiDiagnosticEvent> events = new ArrayList<>();
        KnowledgeProcessingCallbackConsumer consumer = consumer(
                KnowledgeProcessingCallbackOutcome.APPLIED_FAILED, events::add);

        assertThat(consumer.consumeVerified(HEADERS, failedBody()))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.APPLIED_FAILED);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.failureCode()).isEqualTo(CustomerAiDiagnosticFailureCode.EMBEDDING_UNAVAILABLE);
            assertThat(event.retryable()).isTrue();
            assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.FAILED);
        });
    }

    @Test
    void distinguishesDuplicateAndStateConflict() {
        List<CustomerAiDiagnosticEvent> duplicateEvents = new ArrayList<>();
        KnowledgeProcessingCallbackConsumer duplicate = consumer(
                KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE, duplicateEvents::add);

        assertThat(duplicate.consumeVerified(HEADERS, completedBody()))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE);
        assertThat(duplicateEvents).singleElement().satisfies(event ->
                assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.IGNORED_DUPLICATE));

        List<CustomerAiDiagnosticEvent> conflictEvents = new ArrayList<>();
        KnowledgeProcessingCallbackConsumer conflict = consumer(
                KnowledgeProcessingCallbackOutcome.CONFLICT, conflictEvents::add);

        assertThatThrownBy(() -> conflict.consumeVerified(HEADERS, completedBody()))
                .isInstanceOf(KnowledgeProcessingCallbackException.class);
        assertThat(conflictEvents).singleElement().satisfies(event ->
                assertThat(event.failureCode()).isEqualTo(CustomerAiDiagnosticFailureCode.STATE_CONFLICT));
    }

    @Test
    void failingSinkDoesNotChangeCallbackOutcome() {
        KnowledgeProcessingCallbackConsumer consumer = consumer(
                KnowledgeProcessingCallbackOutcome.IGNORED_STALE,
                event -> {
                    throw new IllegalStateException("sink unavailable");
                }
        );

        assertThat(consumer.consumeVerified(HEADERS, completedBody()))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.IGNORED_STALE);
    }

    private KnowledgeProcessingCallbackConsumer consumer(
            KnowledgeProcessingCallbackOutcome outcome,
            com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticSink sink
    ) {
        return new KnowledgeProcessingCallbackConsumer(
                new KnowledgeProcessingCallbackParser(new ObjectMapper()),
                (headers, callback) -> outcome,
                new CustomerAiDiagnosticPublisher(sink, () -> "trace-knowledge")
        );
    }

    private String completedBody() {
        return """
                {"schemaVersion":"1.0","processingId":8001,"knowledgeVersionId":101,
                "status":"COMPLETED","chunkCount":3,"chunkProfile":"HYBRID_POLICY_V1"}
                """;
    }

    private String failedBody() {
        return """
                {"schemaVersion":"1.0","processingId":8001,"knowledgeVersionId":101,
                "status":"FAILED","failureCode":"EMBEDDING_UNAVAILABLE","retryable":true}
                """;
    }
}
