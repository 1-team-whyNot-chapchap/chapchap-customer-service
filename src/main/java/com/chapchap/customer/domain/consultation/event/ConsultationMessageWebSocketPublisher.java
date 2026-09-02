package com.chapchap.customer.domain.consultation.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConsultationMessageWebSocketPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ConsultationMessageSavedEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/consultations/" + event.consultationId(), event.message());
        } catch (RuntimeException exception) {
            log.warn("상담 메시지 실시간 전송에 실패했습니다. consultationId={}", event.consultationId(), exception);
        }
    }
}
