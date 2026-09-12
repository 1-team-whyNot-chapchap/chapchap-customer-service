package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.dto.event.ConsultationHandedOffEvent;
import com.chapchap.customer.domain.consultation.dto.event.ConsultationClosedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:handoff-event;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "customer.ai.consultation-summary.async-enabled=true"
})
@Import(ConsultationSummaryEventListener.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConsultationSummaryEventListenerTest {
    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ConsultationSummaryOrchestrator orchestrator;

    @Test
    void schedulesOnlyCommittedHandoffAndNeverClosure() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new ConsultationHandedOffEvent(501L, 4));
            verifyNoInteractions(orchestrator);
        });
        verify(orchestrator).queue(501L, 4);
        clearInvocations(orchestrator);
        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new ConsultationHandedOffEvent(502L, 8));
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> publisher.publishEvent(new ConsultationClosedEvent(501L)));
        verifyNoInteractions(orchestrator);
    }
}
