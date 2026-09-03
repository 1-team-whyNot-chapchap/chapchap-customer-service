package com.chapchap.customer.domain.knowledge.processing;

import com.chapchap.customer.domain.knowledge.service.KnowledgeActivationService;
import com.chapchap.customer.domain.knowledge.storage.KnowledgeObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeProcessingOrchestratorTest {
    private static final Long KNOWLEDGE_VERSION_ID = 1L;

    @Mock
    private KnowledgeProcessingStateService stateService;

    @Mock
    private KnowledgeProcessingClient knowledgeProcessingClient;

    @Mock
    private KnowledgeObjectStorage knowledgeObjectStorage;

    @Mock
    private KnowledgeActivationService knowledgeActivationService;

    @Mock
    private TaskScheduler knowledgeProcessingTaskScheduler;

    @Captor
    private ArgumentCaptor<Instant> scheduledAtCaptor;

    @InjectMocks
    private KnowledgeProcessingOrchestrator orchestrator;

    @Test
    void completesProcessingAndAttemptsActivationWhenCustomerAiCompletes() {
        when(stateService.startAttempt(eq(KNOWLEDGE_VERSION_ID), any(LocalDateTime.class))).thenReturn(context(1));
        when(knowledgeObjectStorage.createPresignedGetUrl("knowledge/1")).thenReturn("https://private-minio.example/object");
        when(knowledgeProcessingClient.process(any(KnowledgeProcessingRequest.class)))
                .thenReturn(KnowledgeProcessingResult.completed(7));

        orchestrator.process(KNOWLEDGE_VERSION_ID);

        verify(stateService).markCompleted(eq(KNOWLEDGE_VERSION_ID), any(LocalDateTime.class));
        verify(knowledgeActivationService).activateIfDue(eq(KNOWLEDGE_VERSION_ID), any(LocalDateTime.class));
        verify(stateService, never()).markFailed(any(), any(), any(Boolean.class), any());
    }

    @Test
    void schedulesFirstRetryFiveSecondsLaterForRetryableFailure() {
        Instant before = Instant.now();
        when(stateService.startAttempt(eq(KNOWLEDGE_VERSION_ID), any(LocalDateTime.class))).thenReturn(context(1));
        when(knowledgeObjectStorage.createPresignedGetUrl("knowledge/1")).thenReturn("https://private-minio.example/object");
        when(knowledgeProcessingClient.process(any(KnowledgeProcessingRequest.class)))
                .thenReturn(KnowledgeProcessingResult.failed("VECTOR_STORE_UNAVAILABLE", true));

        orchestrator.process(KNOWLEDGE_VERSION_ID);

        verify(knowledgeProcessingTaskScheduler).schedule(any(Runnable.class), scheduledAtCaptor.capture());
        assertThat(scheduledAtCaptor.getValue()).isBetween(before.plusSeconds(5), Instant.now().plusSeconds(6));
        verify(stateService, never()).markFailed(any(), any(), any(Boolean.class), any());
    }

    @Test
    void recordsNonRetryableContractFailureWithoutSchedulingAnotherAttempt() {
        when(stateService.startAttempt(eq(KNOWLEDGE_VERSION_ID), any(LocalDateTime.class))).thenReturn(context(1));
        when(knowledgeObjectStorage.createPresignedGetUrl("knowledge/1")).thenReturn("https://private-minio.example/object");
        when(knowledgeProcessingClient.process(any(KnowledgeProcessingRequest.class)))
                .thenThrow(new KnowledgeProcessingContractException("malformed response"));

        orchestrator.process(KNOWLEDGE_VERSION_ID);

        verify(stateService).markFailed(eq(KNOWLEDGE_VERSION_ID), eq("CONTRACT_ERROR"), eq(false), any(LocalDateTime.class));
        verify(knowledgeProcessingTaskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    private KnowledgeProcessingContext context(int attempt) {
        return new KnowledgeProcessingContext(
                KNOWLEDGE_VERSION_ID,
                attempt,
                "knowledge/1",
                "application/pdf",
                100,
                "faq-policy",
                "order-service",
                "POLICY",
                "v1",
                LocalDateTime.of(2026, 9, 3, 9, 0),
                "HYBRID_POLICY_V1"
        );
    }
}
