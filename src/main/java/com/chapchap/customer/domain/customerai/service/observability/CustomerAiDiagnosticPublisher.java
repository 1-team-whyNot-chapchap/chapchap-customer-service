package com.chapchap.customer.domain.customerai.service.observability;

import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;

import java.util.Objects;
import java.util.function.Function;

public final class CustomerAiDiagnosticPublisher {
    private static final CustomerAiDiagnosticPublisher NO_OP = new CustomerAiDiagnosticPublisher(
            NoOpCustomerAiDiagnosticSink.INSTANCE,
            () -> null
    );

    private final CustomerAiDiagnosticSink sink;
    private final CustomerAiTraceIdProvider traceIdProvider;

    public CustomerAiDiagnosticPublisher(
            CustomerAiDiagnosticSink sink,
            CustomerAiTraceIdProvider traceIdProvider
    ) {
        this.sink = Objects.requireNonNull(sink);
        this.traceIdProvider = Objects.requireNonNull(traceIdProvider);
    }

    public static CustomerAiDiagnosticPublisher noOp() {
        return NO_OP;
    }

    public void publish(Function<String, CustomerAiDiagnosticEvent> eventFactory) {
        try {
            sink.emit(Objects.requireNonNull(eventFactory).apply(traceIdProvider.currentTraceId()));
        } catch (RuntimeException ignored) {
            // Diagnostics must never change the business result.
        }
    }
}
