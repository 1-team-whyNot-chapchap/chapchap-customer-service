package com.chapchap.customer.domain.knowledge.processing;

import com.chapchap.customer.domain.knowledge.service.KnowledgeActivationService;
import com.chapchap.customer.domain.knowledge.storage.KnowledgeObjectStorage;
import com.chapchap.customer.global.error.custom.knowledge.KnowledgeProcessingContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class KnowledgeProcessingOrchestrator {
    private final KnowledgeProcessingStateService stateService;
    private final KnowledgeProcessingClient knowledgeProcessingClient;
    private final KnowledgeObjectStorage knowledgeObjectStorage;
    private final KnowledgeActivationService knowledgeActivationService;
    private final TaskScheduler knowledgeProcessingTaskScheduler;

    public void queue(Long knowledgeVersionId) {
        knowledgeProcessingTaskScheduler.schedule(
                () -> process(knowledgeVersionId),
                Instant.now()
        );
    }

    public void process(Long knowledgeVersionId) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeProcessingContext context = stateService.startAttempt(knowledgeVersionId, now);
        KnowledgeProcessingResult result;
        try {
            result = knowledgeProcessingClient.process(toRequest(context));
        } catch (KnowledgeProcessingContractException exception) {
            stateService.markFailed(knowledgeVersionId, "CONTRACT_ERROR", false, LocalDateTime.now());
            return;
        } catch (RuntimeException exception) {
            retryOrFail(knowledgeVersionId, context.attempt(), "CUSTOMER_AI_UNAVAILABLE");
            return;
        }

        if (!result.completed()) {
            if (result.retryable()) {
                retryOrFail(knowledgeVersionId, context.attempt(), result.failureCode());
                return;
            }
            stateService.markFailed(knowledgeVersionId, result.failureCode(), false, LocalDateTime.now());
            return;
        }

        stateService.markCompleted(knowledgeVersionId, LocalDateTime.now());
        knowledgeActivationService.activateIfDue(knowledgeVersionId, LocalDateTime.now());
    }

    private void retryOrFail(Long knowledgeVersionId, int attempt, String failureCode) {
        if (attempt >= 3) {
            stateService.markFailed(knowledgeVersionId, failureCode, true, LocalDateTime.now());
            return;
        }

        long delaySeconds = attempt == 1 ? 5L : 30L;
        knowledgeProcessingTaskScheduler.schedule(
                () -> process(knowledgeVersionId),
                Instant.now().plusSeconds(delaySeconds)
        );
    }

    private KnowledgeProcessingRequest toRequest(KnowledgeProcessingContext context) {
        return new KnowledgeProcessingRequest(
                context.knowledgeVersionId(),
                context.attempt(),
                new KnowledgeProcessingRequest.Source(
                        knowledgeObjectStorage.createPresignedGetUrl(context.objectKey()),
                        context.contentType(),
                        context.fileSize()
                ),
                new KnowledgeProcessingRequest.Metadata(
                        context.documentKey(),
                        context.sourceService(),
                        context.category(),
                        context.version(),
                        context.effectiveFrom()
                ),
                context.chunkProfile()
        );
    }
}
