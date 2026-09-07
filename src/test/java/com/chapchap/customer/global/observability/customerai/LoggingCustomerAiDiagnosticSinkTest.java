package com.chapchap.customer.global.observability.customerai;

import com.chapchap.customer.domain.consultation.ai.CustomerAiConsultationResult;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.UUID;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingCustomerAiDiagnosticSinkTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void writesOnlyApprovedStructuredCorrelationFields() {

        Logger logger = mock(Logger.class);
        LoggingEventBuilder entry = mock(LoggingEventBuilder.class);
        when(logger.atInfo()).thenReturn(entry);
        when(entry.addKeyValue(anyString(), any(Object.class))).thenReturn(entry);
        LoggingCustomerAiDiagnosticSink sink = new LoggingCustomerAiDiagnosticSink(logger);

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
