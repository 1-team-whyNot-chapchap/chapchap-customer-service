package com.chapchap.customer.domain.consultation.ai;

import com.chapchap.customer.domain.consultation.event.ConsultationAiResponseRequestedEvent;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticEvent;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticOutcome;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.global.security.customerai.CustomerAiAuthenticationUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.consultation-response",
        name = "async-enabled",
        havingValue = "true"
)
public class ConsultationAiResponseOrchestrator {
    private static final Clock KST_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final ConsultationAiLifecycleStateService stateService;
    private final CustomerAiConsultationClient customerAiClient;
    private final TaskScheduler knowledgeProcessingTaskScheduler;
    private final Optional<CustomerAiDiagnosticPublisher> diagnostics;

    public void queue(ConsultationAiResponseRequestedEvent event) {
        knowledgeProcessingTaskScheduler.schedule(
                () -> respond(event),
                Instant.now(KST_CLOCK)
        );
    }

    void respond(ConsultationAiResponseRequestedEvent event) {
        UUID requestId = UUID.randomUUID();
        PreparedConsultationAiRequest prepared;
        try {
            prepared = stateService.prepare(event, requestId).orElse(null);
        } catch (RuntimeException exception) {
            if (stateService.handoffAfterPreparationFailure(
                    event,
                    CustomerAiDiagnosticFailureCode.STATE_CONFLICT.name(),
                    LocalDateTime.now(KST_CLOCK))) {
                diagnosticPublisher().publish(traceId -> CustomerAiDiagnosticEvent.consultationLifecycleFallback(
                        requestId, traceId, event.consultationId(), CustomerAiDiagnosticFailureCode.STATE_CONFLICT));
            }
            return;
        }
        if (prepared == null) {
            return;
        }

        CustomerAiConsultationCommand command = prepared.command();
        try {
            CustomerAiConsultationResult result = customerAiClient.respond(command);
            if (stateService.apply(command, result, LocalDateTime.now(KST_CLOCK))) {
                diagnosticPublisher().publish(traceId -> CustomerAiDiagnosticEvent.consultationLifecycleApplied(
                        command.requestId(),
                        traceId,
                        command.consultationId(),
                        result.route(),
                        CustomerAiDiagnosticOutcome.valueOf(result.decision().name())
                ));
            }
        } catch (CustomerAiConsultationClientException exception) {
            fallback(command, CustomerAiDiagnosticFailureCode.from(exception.reason()));
        } catch (CustomerAiAuthenticationUnavailableException exception) {
            fallback(command, CustomerAiDiagnosticFailureCode.AUTHENTICATION_UNAVAILABLE);
        } catch (RuntimeException exception) {
            fallback(command, CustomerAiDiagnosticFailureCode.STATE_CONFLICT);
        }
    }

    private void fallback(
            CustomerAiConsultationCommand command,
            CustomerAiDiagnosticFailureCode failureCode
    ) {
        if (stateService.handoffAfterFailure(
                command,
                failureCode.name(),
                LocalDateTime.now(KST_CLOCK))) {
            diagnosticPublisher().publish(traceId -> CustomerAiDiagnosticEvent.consultationLifecycleFallback(
                    command.requestId(), traceId, command.consultationId(), failureCode));
        }
    }

    private CustomerAiDiagnosticPublisher diagnosticPublisher() {
        return diagnostics.orElseGet(CustomerAiDiagnosticPublisher::noOp);
    }
}
