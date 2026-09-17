package com.chapchap.customer.domain.customerai.service.observability;

import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import java.util.Objects;

public final class LoggingCustomerAiDiagnosticSink implements CustomerAiDiagnosticSink {
    private final Logger logger;

    public LoggingCustomerAiDiagnosticSink() {
        this(LoggerFactory.getLogger(LoggingCustomerAiDiagnosticSink.class));
    }

    LoggingCustomerAiDiagnosticSink(Logger logger) {
        this.logger = Objects.requireNonNull(logger);
    }

    @Override
    public void emit(CustomerAiDiagnosticEvent event) {
        writeStructuredLog(Objects.requireNonNull(event));
    }

    private void writeStructuredLog(CustomerAiDiagnosticEvent event) {
        LoggingEventBuilder entry = logger.atInfo()
                .addKeyValue("eventType", event.eventType().name())
                .addKeyValue("outcome", event.outcome().name())
                .addKeyValue("requestId", event.requestId());

        // Logstash 형식은 MDC 값을 이미 JSON에 넣는다. 같은 키를 두 번 쓰지 않는다.
        // MDC가 없는 작업에서 이벤트가 traceId를 갖는 경우에는 그 값을 보존한다.
        if (MDC.get("traceId") == null) {
            addIfPresent(entry, "traceId", event.traceId());
        }
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
}
