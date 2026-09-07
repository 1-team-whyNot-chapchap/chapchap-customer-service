package com.chapchap.customer.domain.knowledge.processing.async;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeProcessingCallbackControllerTest {
    private CustomerAiCallbackJwtVerifier verifier;
    private KnowledgeProcessingCallbackConsumer consumer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        verifier = mock(CustomerAiCallbackJwtVerifier.class);
        consumer = mock(KnowledgeProcessingCallbackConsumer.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new KnowledgeProcessingCallbackController(verifier, consumer))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void verifiesServiceJwtBeforeApplyingCompletedCallback() throws Exception {
        when(consumer.consumeVerified(any(), anyString()))
                .thenReturn(KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED);

        mockMvc.perform(post(KnowledgeProcessingCallbackController.PATH)
                        .header("Authorization", "Bearer service.jwt.signature")
                        .header("X-Request-Id", "3c680522-f6db-4b54-b50c-e39c4ec6d366")
                        .header("Idempotency-Key", "8001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedBody()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(verifier).verify("Bearer service.jwt.signature");
        verify(consumer).consumeVerified(any(), anyString());
    }

    @Test
    void rejectsInvalidTokenBeforeParserAndStatePort() throws Exception {
        doThrow(new CustomerAiCallbackAuthenticationException(
                CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN))
                .when(verifier).verify(any());

        mockMvc.perform(post(KnowledgeProcessingCallbackController.PATH)
                        .header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verify(consumer, never()).consumeVerified(any(), anyString());
    }

    @Test
    void returnsRetryableServerFailureWhenJwksIsUnavailable() throws Exception {
        doThrow(new CustomerAiCallbackAuthenticationException(
                CustomerAiCallbackAuthenticationException.Reason.KEY_UNAVAILABLE))
                .when(verifier).verify(any());

        mockMvc.perform(post(KnowledgeProcessingCallbackController.PATH)
                        .header("Authorization", "Bearer service.jwt.signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError());

        verify(consumer, never()).consumeVerified(any(), anyString());
    }

    @Test
    void mapsContractAndStateConflictWithoutLeakingPayload() throws Exception {
        when(consumer.consumeVerified(any(), anyString()))
                .thenThrow(new KnowledgeProcessingCallbackException(
                        KnowledgeProcessingCallbackException.Reason.STATE_CONFLICT));

        mockMvc.perform(post(KnowledgeProcessingCallbackController.PATH)
                        .header("Authorization", "Bearer service.jwt.signature")
                        .header("X-Request-Id", "3c680522-f6db-4b54-b50c-e39c4ec6d366")
                        .header("Idempotency-Key", "8001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedBody()))
                .andExpect(status().isConflict());
    }

    private String completedBody() {
        return """
                {"schemaVersion":"1.0","processingId":8001,"knowledgeVersionId":101,
                 "status":"COMPLETED","chunkCount":12,"chunkProfile":"HYBRID_POLICY_V1"}
                """;
    }
}
