package com.chapchap.customer.domain.knowledge.processing;

import com.chapchap.customer.domain.knowledge.event.KnowledgeVersionRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class KnowledgeProcessingRegistrationListener {
    private final KnowledgeProcessingOrchestrator knowledgeProcessingOrchestrator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startProcessing(KnowledgeVersionRegisteredEvent event) {
        knowledgeProcessingOrchestrator.queue(event.knowledgeVersionId());
    }
}
