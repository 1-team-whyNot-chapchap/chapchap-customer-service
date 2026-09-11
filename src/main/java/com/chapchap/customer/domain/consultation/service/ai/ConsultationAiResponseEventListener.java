package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.consultation.dto.event.ConsultationAiResponseRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.consultation-response",
        name = "async-enabled",
        havingValue = "true"
)
public class ConsultationAiResponseEventListener {
    private final ConsultationAiResponseOrchestrator orchestrator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void requestResponse(ConsultationAiResponseRequestedEvent event) {
        orchestrator.queue(event);
    }
}
