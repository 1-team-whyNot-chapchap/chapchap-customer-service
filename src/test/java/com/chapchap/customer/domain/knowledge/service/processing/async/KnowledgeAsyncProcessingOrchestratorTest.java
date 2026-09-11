package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.dto.processing.async.CustomerAiKnowledgeJobCommand;
import com.chapchap.customer.domain.knowledge.dto.processing.async.PreparedKnowledgeProcessingAttempt;
import com.chapchap.customer.global.exception.knowledge.processing.async.CustomerAiKnowledgeJobClientException;
import com.chapchap.customer.domain.knowledge.response.processing.async.CustomerAiKnowledgeJobAccepted;

import com.chapchap.customer.domain.knowledge.dto.processing.KnowledgeProcessingContext;
import com.chapchap.customer.domain.knowledge.service.storage.KnowledgeObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAsyncProcessingOrchestratorTest {
    @Mock
    private KnowledgeAsyncProcessingStateService stateService;
    @Mock
    private CustomerAiKnowledgeJobClient client;
    @Mock
    private KnowledgeObjectStorage storage;
    @Mock
    private TaskScheduler scheduler;

    private KnowledgeAsyncProcessingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new KnowledgeAsyncProcessingOrchestrator(stateService, client, storage, scheduler);
    }

    @Test
    void preparesPersistsSubmissionAndStoresAcceptedIdentity() {
        PreparedKnowledgeProcessingAttempt prepared = prepared();
        when(stateService.prepareAttempt(any(), any(), any())).thenReturn(Optional.of(prepared));
        when(storage.createPresignedGetUrl("knowledge/source.pdf"))
                .thenReturn("https://minio.internal/knowledge/source.pdf?signature=safe");
        when(client.submit(any())).thenReturn(new CustomerAiKnowledgeJobAccepted(8001L, 101L));

        orchestrator.submit(101L);

        ArgumentCaptor<CustomerAiKnowledgeJobCommand> command =
                ArgumentCaptor.forClass(CustomerAiKnowledgeJobCommand.class);
        verify(client).submit(command.capture());
        assertThat(command.getValue().requestId()).isEqualTo(prepared.requestId());
        assertThat(command.getValue().attempt()).isEqualTo(1);
        verify(stateService).markSubmitted(any(UUID.class), any(LocalDateTime.class));
        verify(stateService).markAccepted(
                any(UUID.class), any(CustomerAiKnowledgeJobAccepted.class), any(LocalDateTime.class));
    }

    @Test
    void recordsRetryableSubmissionFailureWithoutLeakingRawException() {
        when(stateService.prepareAttempt(any(), any(), any())).thenReturn(Optional.of(prepared()));
        when(storage.createPresignedGetUrl("knowledge/source.pdf"))
                .thenReturn("https://minio.internal/knowledge/source.pdf");
        when(client.submit(any())).thenThrow(new CustomerAiKnowledgeJobClientException(
                CustomerAiKnowledgeJobClientException.Reason.TIMEOUT));

        orchestrator.submit(101L);

        verify(stateService).markSubmissionFailed(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq(CustomerAiKnowledgeJobClientException.Reason.TIMEOUT),
                org.mockito.ArgumentMatchers.eq(true),
                any(LocalDateTime.class));
    }

    @Test
    void recordsContractFailureWhenPresignedUrlIsNotHttps() {
        when(stateService.prepareAttempt(any(), any(), any())).thenReturn(Optional.of(prepared()));
        when(storage.createPresignedGetUrl("knowledge/source.pdf"))
                .thenReturn("http://minio.internal/knowledge/source.pdf");

        orchestrator.submit(101L);

        verify(client, never()).submit(any());
        verify(stateService).markSubmissionFailed(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq(CustomerAiKnowledgeJobClientException.Reason.CONTRACT_ERROR),
                org.mockito.ArgumentMatchers.eq(false),
                any(LocalDateTime.class));
    }

    private PreparedKnowledgeProcessingAttempt prepared() {
        return new PreparedKnowledgeProcessingAttempt(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                new KnowledgeProcessingContext(
                        101L,
                        1,
                        "knowledge/source.pdf",
                        "application/pdf",
                        1024L,
                        "refund-policy",
                        "SUBSCRIPTION",
                        "POLICY",
                        "v1",
                        LocalDateTime.of(2026, 9, 7, 16, 0),
                        "HYBRID_POLICY_V1"
                )
        );
    }
}
