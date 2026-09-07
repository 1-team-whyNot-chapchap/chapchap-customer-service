package com.chapchap.customer.global.observability.customerai;

import com.chapchap.customer.domain.consultation.ai.CustomerAiConsultationResult;
import com.chapchap.customer.domain.consultation.ai.currentstate.CurrentStateCapability;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiDiagnosticEventTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void createsConsultationResultWithOnlyTypedOutcomeAndRoute() {
        CustomerAiDiagnosticEvent event = CustomerAiDiagnosticEvent.consultationResult(
                REQUEST_ID,
                "trace-123",
                501,
                CustomerAiConsultationResult.Route.POLICY_AND_STATE,
                CustomerAiDiagnosticOutcome.ANSWER
        );

        assertThat(event.eventType()).isEqualTo(CustomerAiDiagnosticEventType.CONSULTATION_RESULT);
        assertThat(event.requestId()).isEqualTo(REQUEST_ID);
        assertThat(event.traceId()).isEqualTo("trace-123");
        assertThat(event.consultationId()).isEqualTo(501);
        assertThat(event.route()).isEqualTo(CustomerAiConsultationResult.Route.POLICY_AND_STATE);
        assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.ANSWER);
        assertThat(event.failureCode()).isNull();
    }

    @Test
    void distinguishesTransportResultFromPersistedLifecycleAndFallback() {
        CustomerAiDiagnosticEvent applied = CustomerAiDiagnosticEvent.consultationLifecycleApplied(
                REQUEST_ID,
                "trace-123",
                501,
                CustomerAiConsultationResult.Route.POLICY,
                CustomerAiDiagnosticOutcome.ANSWER
        );
        CustomerAiDiagnosticEvent fallback = CustomerAiDiagnosticEvent.consultationLifecycleFallback(
                REQUEST_ID,
                "trace-123",
                501,
                CustomerAiDiagnosticFailureCode.TIMEOUT
        );

        assertThat(applied.eventType())
                .isEqualTo(CustomerAiDiagnosticEventType.CONSULTATION_LIFECYCLE_APPLIED);
        assertThat(applied.metricDimensions()).containsEntry("route", "POLICY");
        assertThat(fallback.eventType())
                .isEqualTo(CustomerAiDiagnosticEventType.CONSULTATION_LIFECYCLE_FALLBACK);
        assertThat(fallback.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.HANDOFF);
        assertThat(fallback.failureCode()).isEqualTo(CustomerAiDiagnosticFailureCode.TIMEOUT);
        assertThat(fallback.metricDimensions()).doesNotContainKeys(
                "requestId", "traceId", "consultationId", "message", "answer", "evidence");
    }

    @Test
    void excludesCorrelationIdsAndMeasurementsFromMetricDimensions() {
        CustomerAiDiagnosticEvent event = CustomerAiDiagnosticEvent.knowledgeCallbackFailed(
                REQUEST_ID,
                "trace-123",
                101,
                8001,
                CustomerAiDiagnosticFailureCode.EMBEDDING_UNAVAILABLE,
                true
        );

        assertThat(event.metricDimensions()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "eventType", "KNOWLEDGE_CALLBACK_RESULT",
                "outcome", "FAILED",
                "failureCode", "EMBEDDING_UNAVAILABLE",
                "retryable", "true"
        ));
        assertThat(event.metricDimensions()).doesNotContainKeys(
                "requestId", "traceId", "consultationId", "knowledgeVersionId",
                "processingId", "latencyMs", "chunkCount"
        );
    }

    @Test
    void keepsSummaryJobIdentifierOutOfTheEventModel() {
        Set<String> instanceFields = Arrays.stream(CustomerAiDiagnosticEvent.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());

        assertThat(instanceFields).containsExactlyInAnyOrder(
                "eventType", "requestId", "traceId", "consultationId", "knowledgeVersionId",
                "processingId", "route", "capabilityId", "outcome", "failureCode",
                "retryable", "latencyMs", "chunkCount"
        );
        assertThat(instanceFields).doesNotContain(
                "summaryJobId", "userId", "authorization", "token", "assertion", "message",
                "prompt", "answer", "summary", "evidence", "body", "throwable", "exception"
        );
    }

    @Test
    void rejectsInvalidTraceAndIdentifiersWithoutEchoingTheirValues() {
        assertThatThrownBy(() -> CustomerAiDiagnosticEvent.currentStateAccessGranted(
                REQUEST_ID,
                "invalid trace value",
                501,
                CurrentStateCapability.PAYMENT_CURRENT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("invalid trace value");

        assertThatThrownBy(() -> CustomerAiDiagnosticEvent.summarySubmitted(
                REQUEST_ID,
                null,
                0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permitsOnlyTypedFailureCodes() {
        assertThat(CustomerAiDiagnosticFailureCode.from(
                com.chapchap.customer.domain.knowledge.processing.async.CustomerAiKnowledgeJobClientException.Reason.TIMEOUT
        )).isEqualTo(CustomerAiDiagnosticFailureCode.TIMEOUT);
        assertThat(CustomerAiDiagnosticFailureCode.from(null))
                .isEqualTo(CustomerAiDiagnosticFailureCode.CONTRACT_ERROR);
    }
}
