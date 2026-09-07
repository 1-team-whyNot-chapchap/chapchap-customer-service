package com.chapchap.customer.global.observability.customerai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.Objects;

public final class MeteredLoggingCustomerAiDiagnosticSink implements CustomerAiDiagnosticSink {
    static final String METRIC_NAME = "chapchap.customer.ai.events";
    private static final String NONE = "none";

    private final MeterRegistry meterRegistry;
    private final Logger logger;

    public MeteredLoggingCustomerAiDiagnosticSink(MeterRegistry meterRegistry) {
        this(meterRegistry, LoggerFactory.getLogger(MeteredLoggingCustomerAiDiagnosticSink.class));
    }

    MeteredLoggingCustomerAiDiagnosticSink(MeterRegistry meterRegistry, Logger logger) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.logger = Objects.requireNonNull(logger);
    }

    @Override
    public void emit(CustomerAiDiagnosticEvent event) {
        CustomerAiDiagnosticEvent diagnosticEvent = Objects.requireNonNull(event);
        writeStructuredLog(diagnosticEvent);
        incrementCounter(diagnosticEvent);
    }

    private void writeStructuredLog(CustomerAiDiagnosticEvent event) {
        LoggingEventBuilder entry = logger.atInfo()
                .addKeyValue("eventType", event.eventType().name())
                .addKeyValue("outcome", event.outcome().name())
                .addKeyValue("requestId", event.requestId());
        addIfPresent(entry, "traceId", event.traceId());
        addIfPresent(entry, "consultationId", event.consultationId());
        addIfPresent(entry, "knowledgeVersionId", event.knowledgeVersionId());
        addIfPresent(entry, "processingId", event.processingId());
        addIfPresent(entry, "route", nameOf(event.route()));
        addIfPresent(entry, "capabilityId", idOf(event));
        addIfPresent(entry, "failureCode", nameOf(event.failureCode()));
        addIfPresent(entry, "retryable", event.retryable());
        addIfPresent(entry, "latencyMs", event.latencyMs());
        addIfPresent(entry, "chunkCount", event.chunkCount());
        entry.log("customer_ai_diagnostic");
    }

    private void incrementCounter(CustomerAiDiagnosticEvent event) {
        Tags tags = Tags.of(
                "eventType", event.eventType().name(),
                "outcome", event.outcome().name(),
                "route", valueOrNone(nameOf(event.route())),
                "capabilityId", valueOrNone(idOf(event)),
                "failureCode", valueOrNone(nameOf(event.failureCode())),
                "retryable", valueOrNone(event.retryable() == null ? null : event.retryable().toString())
        );
        Counter.builder(METRIC_NAME)
                .description("Customer-AI integration diagnostic events")
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    private void addIfPresent(LoggingEventBuilder entry, String key, Object value) {
        if (value != null) {
            entry.addKeyValue(key, value);
        }
    }

    private String idOf(CustomerAiDiagnosticEvent event) {
        return event.capabilityId() == null ? null : event.capabilityId().id();
    }

    private String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String valueOrNone(String value) {
        return value == null ? NONE : value;
    }
}
