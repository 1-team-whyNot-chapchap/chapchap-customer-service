package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.domain.consultation.event.ConsultationClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.consultation-summary",
        name = "async-enabled",
        havingValue = "true"
)
public class ConsultationSummaryEventListener {
    private final ConsultationSummaryOrchestrator orchestrator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startSummary(ConsultationClosedEvent event) {
        orchestrator.queue(event.consultationId());
    }
}
