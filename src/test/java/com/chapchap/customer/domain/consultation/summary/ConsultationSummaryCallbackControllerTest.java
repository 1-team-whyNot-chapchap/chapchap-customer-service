package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.global.error.GlobalExceptionHandler;
import com.chapchap.customer.global.error.custom.customerai.CustomerAiCallbackAuthenticationException;
import com.chapchap.customer.global.security.customerai.CustomerAiCallbackJwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsultationSummaryCallbackControllerTest {
    private CustomerAiCallbackJwtVerifier verifier;
    private ConsultationSummaryCallbackConsumer consumer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        verifier = mock(CustomerAiCallbackJwtVerifier.class);
        consumer = mock(ConsultationSummaryCallbackConsumer.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ConsultationSummaryCallbackController(verifier, consumer))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void verifiesServiceJwtBeforeApplyingSummaryCallback() throws Exception {
        when(consumer.consumeVerified(any(), anyString()))
                .thenReturn(ConsultationSummaryCallbackOutcome.APPLIED_COMPLETED);

        mockMvc.perform(post(ConsultationSummaryCallbackController.PATH)
                        .header("Authorization", "Bearer service.jwt.signature")
                        .header("X-Request-Id", "3c680522-f6db-4b54-b50c-e39c4ec6d366")
                        .header("Idempotency-Key", "7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedBody()))
                .andExpect(status().isNoContent());

        verify(verifier).verify("Bearer service.jwt.signature");
        verify(consumer).consumeVerified(any(), anyString());
    }

    @Test
    void rejectsForbiddenServiceBeforeSummaryConsumer() throws Exception {
        doThrow(new CustomerAiCallbackAuthenticationException(
                CustomerAiCallbackAuthenticationException.Reason.FORBIDDEN_SERVICE))
                .when(verifier).verify(any());

        mockMvc.perform(post(ConsultationSummaryCallbackController.PATH)
                        .header("Authorization", "Bearer wrong-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(consumer, never()).consumeVerified(any(), anyString());
    }

    @Test
    void mapsMalformedHeadersToBadRequestAfterAuthentication() throws Exception {
        mockMvc.perform(post(ConsultationSummaryCallbackController.PATH)
                        .header("Authorization", "Bearer service.jwt.signature")
                        .header("X-Request-Id", "not-a-uuid")
                        .header("Idempotency-Key", "7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedBody()))
                .andExpect(status().isBadRequest());

        verify(consumer, never()).consumeVerified(any(), anyString());
    }

    private String completedBody() {
        return """
                {"schemaVersion":"1.0","summaryJobId":7001,"consultationId":501,
                 "status":"COMPLETED","summary":"환불 정책을 안내함"}
                """;
    }
}
