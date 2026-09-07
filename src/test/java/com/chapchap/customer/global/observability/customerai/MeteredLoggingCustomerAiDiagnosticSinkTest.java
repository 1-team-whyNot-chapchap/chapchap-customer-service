package com.chapchap.customer.global.observability.customerai;

import com.chapchap.customer.domain.consultation.ai.CustomerAiConsultationResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeteredLoggingCustomerAiDiagnosticSinkTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void incrementsCounterWithFixedLowCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MeteredLoggingCustomerAiDiagnosticSink sink = new MeteredLoggingCustomerAiDiagnosticSink(registry);

        sink.emit(CustomerAiDiagnosticEvent.consultationResult(
                REQUEST_ID,
                "trace-123",
                501,
                CustomerAiConsultationResult.Route.POLICY,
                CustomerAiDiagnosticOutcome.ANSWER
        ));

        Counter counter = registry.find(MeteredLoggingCustomerAiDiagnosticSink.METRIC_NAME)
                .tags(
                        "eventType", "CONSULTATION_RESULT",
                        "outcome", "ANSWER",
                        "route", "POLICY",
                        "capabilityId", "none",
                        "failureCode", "none",
                        "retryable", "none"
                )
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTags()).extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder(
                        "eventType", "outcome", "route", "capabilityId", "failureCode", "retryable"
                );
    }

    @Test
    void neverUsesCorrelationOrBusinessIdentifiersAsMetricTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MeteredLoggingCustomerAiDiagnosticSink sink = new MeteredLoggingCustomerAiDiagnosticSink(registry);

        sink.emit(CustomerAiDiagnosticEvent.knowledgeCallbackCompleted(
                REQUEST_ID,
                "trace-123",
                101,
                8001,
                7
        ));

        Counter counter = registry.find(MeteredLoggingCustomerAiDiagnosticSink.METRIC_NAME).counter();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTag("requestId")).isNull();
        assertThat(counter.getId().getTag("traceId")).isNull();
        assertThat(counter.getId().getTag("consultationId")).isNull();
        assertThat(counter.getId().getTag("knowledgeVersionId")).isNull();
        assertThat(counter.getId().getTag("processingId")).isNull();
        assertThat(counter.getId().getTag("userId")).isNull();
    }

    @Test
    void writesOnlyApprovedStructuredCorrelationFields() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Logger logger = mock(Logger.class);
        LoggingEventBuilder entry = mock(LoggingEventBuilder.class);
        when(logger.atInfo()).thenReturn(entry);
        when(entry.addKeyValue(anyString(), any(Object.class))).thenReturn(entry);
        MeteredLoggingCustomerAiDiagnosticSink sink = new MeteredLoggingCustomerAiDiagnosticSink(registry, logger);

        sink.emit(CustomerAiDiagnosticEvent.consultationResult(
                REQUEST_ID,
                "trace-123",
                501,
                CustomerAiConsultationResult.Route.POLICY,
                CustomerAiDiagnosticOutcome.ANSWER
        ));

        verify(entry).addKeyValue("requestId", REQUEST_ID);
        verify(entry).addKeyValue("traceId", "trace-123");
        verify(entry).addKeyValue("consultationId", 501L);
        verify(entry).addKeyValue("route", "POLICY");
        verify(entry, never()).addKeyValue(eq("userId"), any());
        verify(entry, never()).addKeyValue(eq("authorization"), any());
        verify(entry, never()).addKeyValue(eq("token"), any());
        verify(entry, never()).addKeyValue(eq("assertion"), any());
        verify(entry, never()).addKeyValue(eq("prompt"), any());
        verify(entry, never()).addKeyValue(eq("answer"), any());
        verify(entry).log("customer_ai_diagnostic");
    }
}
