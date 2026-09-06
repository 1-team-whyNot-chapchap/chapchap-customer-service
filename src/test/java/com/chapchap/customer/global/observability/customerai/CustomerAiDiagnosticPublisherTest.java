package com.chapchap.customer.global.observability.customerai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CustomerAiDiagnosticPublisherTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void suppliesValidatedMdcTraceIdToTheEvent() {
        List<CustomerAiDiagnosticEvent> events = new ArrayList<>();
        MDC.put("traceId", "trace-123");
        CustomerAiDiagnosticPublisher publisher = new CustomerAiDiagnosticPublisher(
                events::add,
                new MdcCustomerAiTraceIdProvider()
        );

        publisher.publish(traceId -> CustomerAiDiagnosticEvent.summarySubmitted(
                REQUEST_ID, traceId, 501));

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.traceId()).isEqualTo("trace-123"));
    }

    @Test
    void omitsInvalidMdcTraceId() {
        MDC.put("traceId", "private value with spaces");
        MdcCustomerAiTraceIdProvider provider = new MdcCustomerAiTraceIdProvider();

        assertThat(provider.currentTraceId()).isNull();
    }

    @Test
    void sinkFailureNeverChangesTheBusinessFlow() {
        CustomerAiDiagnosticPublisher publisher = new CustomerAiDiagnosticPublisher(
                event -> {
                    throw new IllegalStateException("diagnostic backend unavailable");
                },
                () -> "trace-123"
        );

        assertThatCode(() -> publisher.publish(traceId ->
                CustomerAiDiagnosticEvent.summarySubmitted(REQUEST_ID, traceId, 501)))
                .doesNotThrowAnyException();
    }

    @Test
    void traceProviderOrFactoryFailureIsAlsoIsolated() {
        CustomerAiDiagnosticPublisher failingTraceProvider = new CustomerAiDiagnosticPublisher(
                event -> {
                },
                () -> {
                    throw new IllegalStateException("trace unavailable");
                }
        );
        CustomerAiDiagnosticPublisher failingFactory = new CustomerAiDiagnosticPublisher(
                event -> {
                },
                () -> null
        );

        assertThatCode(() -> failingTraceProvider.publish(traceId ->
                CustomerAiDiagnosticEvent.summarySubmitted(REQUEST_ID, traceId, 501)))
                .doesNotThrowAnyException();
        assertThatCode(() -> failingFactory.publish(traceId -> {
            throw new IllegalStateException("factory unavailable");
        })).doesNotThrowAnyException();
    }
}
