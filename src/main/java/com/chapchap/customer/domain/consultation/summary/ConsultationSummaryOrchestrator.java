package com.chapchap.customer.domain.consultation.summary;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.consultation-summary",
        name = "async-enabled",
        havingValue = "true"
)
public class ConsultationSummaryOrchestrator {
    private static final Clock KST_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final ConsultationSummaryStateService stateService;
    private final CustomerAiConsultationSummaryClient customerAiClient;
    private final TaskScheduler knowledgeProcessingTaskScheduler;

    public void queue(Long consultationId) {
        knowledgeProcessingTaskScheduler.schedule(
                () -> submit(consultationId),
                Instant.now(KST_CLOCK)
        );
    }

    void submit(Long consultationId) {
        PreparedConsultationSummaryJob prepared = stateService
                .prepare(consultationId, LocalDateTime.now(KST_CLOCK))
                .orElse(null);
        if (prepared == null) {
            return;
        }

        CustomerAiConsultationSummaryCommand command = new CustomerAiConsultationSummaryCommand(
                prepared.requestId(),
                prepared.summaryJobId(),
                prepared.consultationId(),
                com.chapchap.customer.domain.consultation.entity.ConsultationStatus.CLOSED,
                prepared.messages()
        );
        stateService.markSubmitted(prepared.summaryJobId(), LocalDateTime.now(KST_CLOCK));

        CustomerAiConsultationSummaryAccepted accepted;
        try {
            accepted = customerAiClient.submit(command);
        } catch (CustomerAiConsultationSummaryClientException exception) {
            stateService.markSubmissionFailed(
                    prepared.summaryJobId(),
                    exception.reason(),
                    exception.retryable(),
                    LocalDateTime.now(KST_CLOCK));
            return;
        } catch (RuntimeException exception) {
            stateService.markSubmissionFailed(
                    prepared.summaryJobId(),
                    CustomerAiConsultationSummaryClientException.Reason.DEPENDENCY_UNAVAILABLE,
                    true,
                    LocalDateTime.now(KST_CLOCK));
            return;
        }
        stateService.markAccepted(prepared, accepted, LocalDateTime.now(KST_CLOCK));
    }
}
