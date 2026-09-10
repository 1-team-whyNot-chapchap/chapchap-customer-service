package com.chapchap.customer.domain.knowledge.service.processing;

import com.chapchap.customer.domain.knowledge.dto.event.KnowledgeVersionRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.knowledge-processing",
        name = "async-enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class KnowledgeProcessingRegistrationListener {
    private final KnowledgeProcessingOrchestrator knowledgeProcessingOrchestrator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startProcessing(KnowledgeVersionRegisteredEvent event) {
        knowledgeProcessingOrchestrator.queue(event.knowledgeVersionId());
    }
}
