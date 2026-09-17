package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.dto.processing.KnowledgeProcessingContext;
import com.chapchap.customer.domain.knowledge.dto.processing.async.CustomerAiKnowledgeJobCommand;
import com.chapchap.customer.domain.knowledge.dto.processing.async.PreparedKnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.response.processing.async.CustomerAiKnowledgeJobAccepted;
import com.chapchap.customer.domain.knowledge.service.storage.KnowledgeObjectStorage;
import com.chapchap.customer.global.exception.knowledge.processing.async.CustomerAiKnowledgeJobClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeAsyncProcessingOrchestratorTest {
    private KnowledgeAsyncProcessingStateService state;
    private CustomerAiKnowledgeJobClient client;
    private KnowledgeObjectStorage storage;
    private TaskScheduler scheduler;
    private KnowledgeAsyncProcessingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        state = mock(KnowledgeAsyncProcessingStateService.class);
        client = mock(CustomerAiKnowledgeJobClient.class);
        storage = mock(KnowledgeObjectStorage.class);
        scheduler = mock(TaskScheduler.class);
        orchestrator = new KnowledgeAsyncProcessingOrchestrator(state, client, storage, scheduler);
    }

    @Test
    void preparesPersistsSubmissionAndStoresAcceptedIdentity() {
        var prepared = prepared();
        when(state.prepareAttempt(anyLong(), any(), any(), eq(0))).thenReturn(Optional.of(prepared));
        when(storage.createPresignedGetUrl("knowledge/source.pdf")).thenReturn("https://minio.internal/source.pdf");
        when(client.submit(any())).thenReturn(new CustomerAiKnowledgeJobAccepted(8001L, 101L));
        orchestrator.submit(101L);
        var command = ArgumentCaptor.forClass(CustomerAiKnowledgeJobCommand.class);
        verify(client).submit(command.capture());
        assertEquals(prepared.requestId(), command.getValue().requestId());
        assertEquals(1, command.getValue().attempt());
        verify(state).markSubmitted(any(UUID.class), any(LocalDateTime.class));
        verify(state).markAccepted(any(UUID.class), any(CustomerAiKnowledgeJobAccepted.class), any(LocalDateTime.class));
    }

    @Test
    void recordsRetryableSubmissionFailureWithoutLeakingRawException() {
        when(state.prepareAttempt(anyLong(), any(), any(), eq(0))).thenReturn(Optional.of(prepared()));
        when(storage.createPresignedGetUrl("knowledge/source.pdf")).thenReturn("https://minio.internal/source.pdf");
        when(client.submit(any())).thenThrow(new CustomerAiKnowledgeJobClientException(
                CustomerAiKnowledgeJobClientException.Reason.TIMEOUT));
        orchestrator.submit(101L);
        verify(state).markSubmissionFailed(any(UUID.class),
                eq(CustomerAiKnowledgeJobClientException.Reason.TIMEOUT), eq(true), any(LocalDateTime.class));
    }

    @Test
    void recordsContractFailureWhenPresignedUrlIsNotHttps() {
        when(state.prepareAttempt(anyLong(), any(), any(), eq(0))).thenReturn(Optional.of(prepared()));
        when(storage.createPresignedGetUrl("knowledge/source.pdf")).thenReturn("http://minio.internal/source.pdf");
        orchestrator.submit(101L);
        verifyNoInteractions(client);
        verify(state).markSubmissionFailed(any(UUID.class),
                eq(CustomerAiKnowledgeJobClientException.Reason.CONTRACT_ERROR), eq(false), any(LocalDateTime.class));
    }

    @Test
    void initialSchedulingCarriesExpectedAttemptZero() {
        orchestrator.queueInitial(101L);
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Instant.class));
        when(state.prepareAttempt(anyLong(), any(), any(), eq(0))).thenReturn(Optional.empty());
        task.getValue().run();
        verify(state).prepareAttempt(eq(101L), any(), any(), eq(0));
        verifyNoInteractions(client);
    }

    @Test
    void retrySchedulingCarriesCompletedAttemptAndFiveSecondDelay() {
        Instant before = Instant.now();
        orchestrator.queueRetry(101L, 1);
        Instant after = Instant.now();
        var task = ArgumentCaptor.forClass(Runnable.class);
        var due = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(task.capture(), due.capture());
        assertFalse(due.getValue().isBefore(before.plusSeconds(5)));
        assertFalse(due.getValue().isAfter(after.plusSeconds(5)));
        when(state.prepareAttempt(anyLong(), any(), any(), eq(1))).thenReturn(Optional.empty());
        task.getValue().run();
        verify(state).prepareAttempt(eq(101L), any(), any(), eq(1));
    }

    @Test
    void secondRetryUsesThirtySecondDelay() {
        Instant before = Instant.now();
        orchestrator.queueRetry(101L, 2);
        Instant after = Instant.now();
        var due = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(any(Runnable.class), due.capture());
        assertFalse(due.getValue().isBefore(before.plusSeconds(30)));
        assertFalse(due.getValue().isAfter(after.plusSeconds(30)));
    }

    @Test
    void noRetryAfterThirdAttempt() {
        orchestrator.queueRetry(101L, 3);
        verifyNoInteractions(scheduler);
    }

    private PreparedKnowledgeProcessingAttempt prepared() {
        return new PreparedKnowledgeProcessingAttempt(UUID.fromString("11111111-1111-4111-8111-111111111111"),
                new KnowledgeProcessingContext(101L, 1, "knowledge/source.pdf", "application/pdf", 1024L,
                        "refund-policy", "SUBSCRIPTION", "POLICY", "v1",
                        LocalDateTime.of(2026, 9, 17, 12, 0), "HYBRID_POLICY_V1"));
    }
}
