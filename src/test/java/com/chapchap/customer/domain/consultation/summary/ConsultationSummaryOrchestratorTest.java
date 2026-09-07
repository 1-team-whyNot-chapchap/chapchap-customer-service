package com.chapchap.customer.domain.consultation.summary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationSummaryOrchestratorTest {
    @Mock
    private ConsultationSummaryStateService stateService;
    @Mock
    private CustomerAiConsultationSummaryClient client;
    @Mock
    private TaskScheduler scheduler;

    private ConsultationSummaryOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ConsultationSummaryOrchestrator(stateService, client, scheduler);
    }

    @Test
    void submitsClosedConsultationMessagesAndStoresAcceptedIdentity() {
        PreparedConsultationSummaryJob prepared = prepared();
        when(stateService.prepare(eq(501L), any())).thenReturn(Optional.of(prepared));
        when(client.submit(any())).thenReturn(new CustomerAiConsultationSummaryAccepted(7001L, 501L));

        orchestrator.submit(501L);

        ArgumentCaptor<CustomerAiConsultationSummaryCommand> command =
                ArgumentCaptor.forClass(CustomerAiConsultationSummaryCommand.class);
        verify(client).submit(command.capture());
        assertThat(command.getValue().consultationStatus().name()).isEqualTo("CLOSED");
        assertThat(command.getValue().messages()).hasSize(2);
        verify(stateService).markSubmitted(eq(7001L), any(LocalDateTime.class));
        verify(stateService).markAccepted(eq(prepared), any(), any(LocalDateTime.class));
    }

    @Test
    void recordsRetryableTimeoutWithoutReopeningConsultation() {
        when(stateService.prepare(eq(501L), any())).thenReturn(Optional.of(prepared()));
        when(client.submit(any())).thenThrow(new CustomerAiConsultationSummaryClientException(
                CustomerAiConsultationSummaryClientException.Reason.TIMEOUT));

        orchestrator.submit(501L);

        verify(stateService).markSubmissionFailed(
                eq(7001L),
                eq(CustomerAiConsultationSummaryClientException.Reason.TIMEOUT),
                eq(true),
                any(LocalDateTime.class));
    }

    private PreparedConsultationSummaryJob prepared() {
        return new PreparedConsultationSummaryJob(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                7001L,
                501L,
                List.of(
                        new CustomerAiConsultationSummaryCommand.Message(
                                CustomerAiConsultationSummaryCommand.SenderType.USER, "배송 문의"),
                        new CustomerAiConsultationSummaryCommand.Message(
                                CustomerAiConsultationSummaryCommand.SenderType.ADMIN, "확인했습니다.")));
    }
}
