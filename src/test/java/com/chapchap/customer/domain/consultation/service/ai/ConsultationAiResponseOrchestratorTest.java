package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationCommand;
import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationResult;
import com.chapchap.customer.domain.consultation.dto.ai.PreparedConsultationAiRequest;
import com.chapchap.customer.global.exception.consultation.ai.CustomerAiConsultationClientException;

import com.chapchap.customer.domain.consultation.dto.event.ConsultationAiResponseRequestedEvent;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationAiResponseOrchestratorTest {
    @Mock
    private ConsultationAiLifecycleStateService stateService;
    @Mock
    private CustomerAiConsultationClient client;
    @Mock
    private TaskScheduler scheduler;
    @Mock
    private CustomerAiDiagnosticPublisher diagnostics;

    private ConsultationAiResponseOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ConsultationAiResponseOrchestrator(
                stateService, client, scheduler, Optional.of(diagnostics));
    }

    @Test
    void appliesValidatedAnswerAndEmitsLifecycleObservation() {
        CustomerAiConsultationCommand command = command();
        CustomerAiConsultationResult result = new CustomerAiConsultationResult(
                command.requestId(),
                CustomerAiConsultationResult.Decision.ANSWER,
                "답변",
                CustomerAiConsultationResult.Route.USER_STATE,
                false,
                false,
                List.of()
        );
        when(stateService.prepare(any(), any())).thenReturn(Optional.of(
                new PreparedConsultationAiRequest(command)));
        when(client.respond(command)).thenReturn(result);
        when(stateService.apply(any(), any(), any())).thenReturn(true);

        orchestrator.respond(event());

        verify(stateService).apply(any(), any(), any());
        verify(diagnostics).publish(any());
    }

    @Test
    void convertsTimeoutToSafeHandoffAndLifecycleObservation() {
        CustomerAiConsultationCommand command = command();
        when(stateService.prepare(any(), any())).thenReturn(Optional.of(
                new PreparedConsultationAiRequest(command)));
        when(client.respond(command)).thenThrow(new CustomerAiConsultationClientException(
                CustomerAiConsultationClientException.Reason.TIMEOUT));
        when(stateService.handoffAfterFailure(any(), any(), any())).thenReturn(true);

        orchestrator.respond(event());

        verify(stateService).handoffAfterFailure(any(), any(), any());
        verify(diagnostics).publish(any());
    }

    @Test
    void convertsPreparationFailureToSafeHandoff() {
        when(stateService.prepare(any(), any())).thenThrow(new IllegalStateException("DB state conflict"));
        when(stateService.handoffAfterPreparationFailure(any(), any(), any())).thenReturn(true);

        orchestrator.respond(event());

        verify(stateService).handoffAfterPreparationFailure(any(), any(), any());
        verify(diagnostics).publish(any());
    }

    private ConsultationAiResponseRequestedEvent event() {
        return new ConsultationAiResponseRequestedEvent(501L, 9002L, 42L, RolePolicy.CUSTOMER);
    }

    private CustomerAiConsultationCommand command() {
        UUID requestId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        return new CustomerAiConsultationCommand(
                requestId,
                501L,
                9002L,
                new CustomerAiSubjectAssertionRequest(
                        42L, RolePolicy.CUSTOMER, List.of("customer-ai.policy.read"), requestId, 501L),
                "문의",
                List.of()
        );
    }
}
