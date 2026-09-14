package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.dto.summary.CustomerAiConsultationSummaryCommand;
import com.chapchap.customer.domain.consultation.dto.summary.PreparedConsultationSummaryJob;
import com.chapchap.customer.global.exception.consultation.summary.CustomerAiConsultationSummaryClientException;
import com.chapchap.customer.domain.consultation.response.summary.CustomerAiConsultationSummaryAccepted;

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

    public void queue(Long consultationId, int lastMessageSequenceNo) {
        knowledgeProcessingTaskScheduler.schedule(
                () -> submit(consultationId, lastMessageSequenceNo),
                Instant.now(KST_CLOCK)
        );
    }

    void submit(Long consultationId, int lastMessageSequenceNo) {
        PreparedConsultationSummaryJob prepared = stateService
                .claimForConsultation(consultationId, LocalDateTime.now(KST_CLOCK))
                .orElse(null);
        if (prepared == null) {
            return;
        }

        deliver(prepared);
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${customer.ai.consultation-summary.recovery-delay-ms:30000}", initialDelay = 10000)
    public void recover() {
        for (Long id : stateService.recoveryCandidates(LocalDateTime.now(KST_CLOCK))) {
            try {
                stateService.claim(id, LocalDateTime.now(KST_CLOCK)).ifPresent(this::deliver);
            } catch (RuntimeException exception) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Summary recovery failed for job {}", id);
            }
        }
    }

    private void deliver(PreparedConsultationSummaryJob prepared) {
        try {
            CustomerAiConsultationSummaryCommand command = new CustomerAiConsultationSummaryCommand(
                    prepared.requestId(), prepared.summaryJobId(), prepared.consultationId(),
                    com.chapchap.customer.domain.consultation.constant.ConsultationStatus.WAITING_ADMIN,
                    prepared.messages()
            );
            CustomerAiConsultationSummaryAccepted accepted = customerAiClient.submit(command);
            stateService.markAccepted(prepared, accepted, LocalDateTime.now(KST_CLOCK));
        } catch (IllegalArgumentException exception) {
            stateService.markAttemptFailed(
                    prepared,
                    CustomerAiConsultationSummaryClientException.Reason.CONTRACT_ERROR,
                    false,
                    LocalDateTime.now(KST_CLOCK));
        } catch (CustomerAiConsultationSummaryClientException exception) {
            stateService.markAttemptFailed(
                    prepared,
                    exception.reason(),
                    exception.retryable(),
                    LocalDateTime.now(KST_CLOCK));
            return;
        } catch (RuntimeException exception) {
            stateService.markAttemptFailed(
                    prepared,
                    CustomerAiConsultationSummaryClientException.Reason.DEPENDENCY_UNAVAILABLE,
                    true,
                    LocalDateTime.now(KST_CLOCK));
            return;
        }
    }
}
