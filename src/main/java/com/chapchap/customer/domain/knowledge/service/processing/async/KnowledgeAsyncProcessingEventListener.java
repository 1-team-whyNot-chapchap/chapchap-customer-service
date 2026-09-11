package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCompletedEvent;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingRetryRequestedEvent;

import com.chapchap.customer.domain.knowledge.dto.event.KnowledgeVersionRegisteredEvent;
import com.chapchap.customer.domain.knowledge.service.KnowledgeActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.knowledge-processing",
        name = "async-enabled",
        havingValue = "true"
)
public class KnowledgeAsyncProcessingEventListener {
    private static final Clock KST_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final KnowledgeAsyncProcessingOrchestrator orchestrator;
    private final KnowledgeActivationService activationService;
    private final TaskScheduler knowledgeProcessingTaskScheduler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startProcessing(KnowledgeVersionRegisteredEvent event) {
        orchestrator.queueInitial(event.knowledgeVersionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void retryProcessing(KnowledgeProcessingRetryRequestedEvent event) {
        orchestrator.queueRetry(event.knowledgeVersionId(), event.completedAttempt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void activateCompletedVersion(KnowledgeProcessingCompletedEvent event) {
        knowledgeProcessingTaskScheduler.schedule(
                () -> activationService.activateIfDue(
                        event.knowledgeVersionId(), LocalDateTime.now(KST_CLOCK)),
                Instant.now(KST_CLOCK)
        );
    }
}
