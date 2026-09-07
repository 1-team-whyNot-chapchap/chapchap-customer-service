package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.global.error.custom.consultation.ConsultationNotFoundException;
import com.chapchap.customer.global.error.custom.consultation.ConsultationStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.consultation-summary",
        name = "async-enabled",
        havingValue = "true"
)
public class ConsultationSummaryStateService {
    private final ConsultationRepository consultationRepository;
    private final ConsultationMessageRepository messageRepository;
    private final ConsultationSummaryJobRepository jobRepository;

    @Transactional
    public Optional<PreparedConsultationSummaryJob> prepare(
            Long consultationId,
            LocalDateTime now
    ) {
        Consultation consultation = consultationRepository.findByIdForMessageWrite(consultationId)
                .orElseThrow(ConsultationNotFoundException::new);
        if (consultation.getStatus() != ConsultationStatus.CLOSED) {
            throw new ConsultationStateException("종료된 상담만 요약할 수 있습니다.");
        }
        if (jobRepository.findByConsultationId(consultationId).isPresent()) {
            return Optional.empty();
        }

        var messages = messageRepository.findByConsultation_IdOrderBySequenceNoAsc(consultationId)
                .stream()
                .map(message -> new CustomerAiConsultationSummaryCommand.Message(
                        CustomerAiConsultationSummaryCommand.SenderType.valueOf(
                                message.getSenderType().name()),
                        message.getContent()
                ))
                .toList();
        if (messages.isEmpty()) {
            throw new ConsultationStateException("요약할 상담 메시지가 없습니다.");
        }

        ConsultationSummaryJob job = jobRepository.saveAndFlush(
                ConsultationSummaryJob.create(consultationId, UUID.randomUUID(), now));
        return Optional.of(new PreparedConsultationSummaryJob(
                job.getRequestId(), job.getId(), consultationId, messages));
    }

    @Transactional
    public void markSubmitted(Long summaryJobId, LocalDateTime now) {
        requireJob(summaryJobId).markSubmitted(now);
    }

    @Transactional
    public void markAccepted(
            PreparedConsultationSummaryJob prepared,
            CustomerAiConsultationSummaryAccepted accepted,
            LocalDateTime now
    ) {
        ConsultationSummaryJob job = requireJob(prepared.summaryJobId());
        if (accepted.summaryJobId() != job.getId()
                || accepted.consultationId() != job.getConsultationId()) {
            throw new ConsultationStateException("Customer-AI 요약 수락 identity가 충돌합니다.");
        }
        job.markAccepted(now);
    }

    @Transactional
    public void markSubmissionFailed(
            Long summaryJobId,
            CustomerAiConsultationSummaryClientException.Reason reason,
            boolean retryable,
            LocalDateTime now
    ) {
        ConsultationSummaryJob job = requireJob(summaryJobId);
        job.markSubmissionFailed(reason.name(), retryable, now);
    }

    private ConsultationSummaryJob requireJob(Long summaryJobId) {
        return jobRepository.findByIdForUpdate(summaryJobId)
                .orElseThrow(() -> new ConsultationStateException("상담 요약 Job을 찾을 수 없습니다."));
    }
}
