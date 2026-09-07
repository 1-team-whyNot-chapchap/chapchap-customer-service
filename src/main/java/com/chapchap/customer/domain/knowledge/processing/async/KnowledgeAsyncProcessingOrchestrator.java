package com.chapchap.customer.domain.knowledge.processing.async;

import com.chapchap.customer.domain.knowledge.processing.KnowledgeProcessingContext;
import com.chapchap.customer.domain.knowledge.storage.KnowledgeObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.net.URI;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.knowledge-processing",
        name = "async-enabled",
        havingValue = "true"
)
public class KnowledgeAsyncProcessingOrchestrator {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock KST_CLOCK = Clock.system(KST);

    private final KnowledgeAsyncProcessingStateService stateService;
    private final CustomerAiKnowledgeJobClient customerAiKnowledgeJobClient;
    private final KnowledgeObjectStorage knowledgeObjectStorage;
    private final TaskScheduler knowledgeProcessingTaskScheduler;

    public void queueInitial(Long knowledgeVersionId) {
        knowledgeProcessingTaskScheduler.schedule(
                () -> submit(knowledgeVersionId),
                Instant.now(KST_CLOCK)
        );
    }

    public void queueRetry(Long knowledgeVersionId, int completedAttempt) {
        long delaySeconds = completedAttempt == 1 ? 5L : 30L;
        knowledgeProcessingTaskScheduler.schedule(
                () -> submit(knowledgeVersionId),
                Instant.now(KST_CLOCK).plusSeconds(delaySeconds)
        );
    }

    void submit(Long knowledgeVersionId) {
        UUID requestId = UUID.randomUUID();
        PreparedKnowledgeProcessingAttempt prepared = stateService.prepareAttempt(
                        knowledgeVersionId, requestId, LocalDateTime.now(KST_CLOCK))
                .orElse(null);
        if (prepared == null) {
            return;
        }
        CustomerAiKnowledgeJobCommand command;
        try {
            command = toCommand(prepared);
        } catch (IllegalArgumentException exception) {
            stateService.markSubmissionFailed(
                    requestId,
                    CustomerAiKnowledgeJobClientException.Reason.CONTRACT_ERROR,
                    false,
                    LocalDateTime.now(KST_CLOCK));
            return;
        } catch (RuntimeException exception) {
            stateService.markSubmissionFailed(
                    requestId,
                    CustomerAiKnowledgeJobClientException.Reason.DEPENDENCY_UNAVAILABLE,
                    true,
                    LocalDateTime.now(KST_CLOCK));
            return;
        }
        stateService.markSubmitted(requestId, LocalDateTime.now(KST_CLOCK));
        CustomerAiKnowledgeJobAccepted accepted;
        try {
            accepted = customerAiKnowledgeJobClient.submit(command);
        } catch (CustomerAiKnowledgeJobClientException exception) {
            stateService.markSubmissionFailed(
                    requestId, exception.reason(), exception.retryable(), LocalDateTime.now(KST_CLOCK));
            return;
        } catch (RuntimeException exception) {
            stateService.markSubmissionFailed(
                    requestId,
                    CustomerAiKnowledgeJobClientException.Reason.DEPENDENCY_UNAVAILABLE,
                    true,
                    LocalDateTime.now(KST_CLOCK));
            return;
        }
        stateService.markAccepted(requestId, accepted, LocalDateTime.now(KST_CLOCK));
    }

    private CustomerAiKnowledgeJobCommand toCommand(PreparedKnowledgeProcessingAttempt prepared) {
        KnowledgeProcessingContext context = prepared.context();
        return new CustomerAiKnowledgeJobCommand(
                prepared.requestId(),
                context.knowledgeVersionId(),
                context.attempt(),
                new CustomerAiKnowledgeJobCommand.Source(
                        URI.create(knowledgeObjectStorage.createPresignedGetUrl(context.objectKey())),
                        context.contentType(),
                        context.fileSize()
                ),
                new CustomerAiKnowledgeJobCommand.Metadata(
                        context.documentKey(),
                        context.sourceService(),
                        context.category(),
                        context.version(),
                        context.effectiveFrom().atZone(KST).toOffsetDateTime()
                ),
                context.chunkProfile()
        );
    }
}
