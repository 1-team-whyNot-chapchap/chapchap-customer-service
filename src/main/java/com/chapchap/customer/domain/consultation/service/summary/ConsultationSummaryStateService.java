package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.dto.summary.CustomerAiConsultationSummaryCommand;
import com.chapchap.customer.domain.consultation.dto.summary.PreparedConsultationSummaryJob;
import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummaryJob;
import com.chapchap.customer.global.exception.consultation.summary.CustomerAiConsultationSummaryClientException;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryJobRepository;
import com.chapchap.customer.domain.consultation.response.summary.CustomerAiConsultationSummaryAccepted;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.constant.ConsultationStatus;
import com.chapchap.customer.domain.consultation.constant.ConsultationSenderType;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.global.exception.consultation.ConsultationNotFoundException;
import com.chapchap.customer.global.exception.consultation.ConsultationStateException;
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
            int lastMessageSequenceNo,
            LocalDateTime now
    ) {
        Consultation consultation = consultationRepository.findByIdForMessageWrite(consultationId)
                .orElseThrow(ConsultationNotFoundException::new);
        if (consultation.getStatus() == ConsultationStatus.AI_HANDLING) {
            throw new ConsultationStateException("상담사에게 전환된 상담만 요약할 수 있습니다.");
        }
        if (jobRepository.findByConsultationId(consultationId).isPresent()) {
            return Optional.empty();
        }

        var messages = messageRepository.findByConsultation_IdOrderBySequenceNoAsc(consultationId)
                .stream()
                .filter(message -> message.getSequenceNo() <= lastMessageSequenceNo)
                .filter(message -> message.getSenderType() == ConsultationSenderType.USER
                        || message.getSenderType() == ConsultationSenderType.AI)
                .map(message -> new CustomerAiConsultationSummaryCommand.Message(
                        CustomerAiConsultationSummaryCommand.SenderType.valueOf(
                                message.getSenderType().name()),
                        message.getContent()
                ))
                .toList();
        if (messages.isEmpty()) {
            throw new ConsultationStateException("요약할 상담 메시지가 없습니다.");
        }

        ConsultationSummaryJob job = ConsultationSummaryJob.create(consultationId, UUID.randomUUID(), now);
        job.rememberCutoff(lastMessageSequenceNo);
        job = jobRepository.saveAndFlush(job);
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
        if (!job.getRequestId().equals(prepared.requestId())) return;
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

    @Transactional
    public Optional<PreparedConsultationSummaryJob> claim(Long jobId, LocalDateTime now) {
        ConsultationSummaryJob job = requireJob(jobId);
        if (job.getAttemptCount() >= 3
                && java.util.Set.of("SUBMITTED", "ACCEPTED").contains(job.getStatus().name())
                && !job.getUpdatedAt().isAfter(now.minusSeconds(120))) {
            job.markSubmissionFailed("PROCESSING_TIMEOUT", true, now);
        }
        if (!job.canRecover(now)) return Optional.empty();
        if (job.isTerminal()) job.retry(now);
        var messages = messageRepository.findByConsultation_IdOrderBySequenceNoAsc(job.getConsultationId())
                .stream().filter(message -> message.getSequenceNo() <= job.getCutoffSequenceNo())
                .filter(message -> message.getSenderType() == ConsultationSenderType.USER
                        || message.getSenderType() == ConsultationSenderType.AI)
                .map(message -> new CustomerAiConsultationSummaryCommand.Message(
                        CustomerAiConsultationSummaryCommand.SenderType.valueOf(message.getSenderType().name()),
                        message.getContent())).toList();
        job.markSubmitted(now);
        return Optional.of(new PreparedConsultationSummaryJob(job.getRequestId(), job.getId(),
                job.getConsultationId(), messages));
    }

    @Transactional
    public Optional<PreparedConsultationSummaryJob> claimForConsultation(Long consultationId, LocalDateTime now) {
        return jobRepository.findByConsultationId(consultationId)
                .flatMap(job -> claim(job.getId(), now));
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> recoveryCandidates(LocalDateTime now) {
        return jobRepository.findRecoveryCandidates(now.minusSeconds(30),
                org.springframework.data.domain.PageRequest.of(0, 50));
    }

    @Transactional
    public void markAttemptFailed(PreparedConsultationSummaryJob prepared,
            CustomerAiConsultationSummaryClientException.Reason reason, boolean retryable, LocalDateTime now) {
        var job = requireJob(prepared.summaryJobId());
        if (job.getRequestId().equals(prepared.requestId())) job.markSubmissionFailed(reason.name(), retryable, now);
    }

    @Transactional
    public void retryManually(Long consultationId, LocalDateTime now) {
        var existing = jobRepository.findByConsultationId(consultationId)
                .orElseThrow(() -> new ConsultationStateException("등록된 요약 작업이 없습니다."));
        var job = requireJob(existing.getId());
        if (job.getCutoffSequenceNo() == null || !java.util.Set.of("FAILED", "SUBMISSION_FAILED")
                .contains(job.getStatus().name())) {
            throw new ConsultationStateException("이 요약은 재처리할 수 없습니다.");
        }
        if (job.getUpdatedAt().isAfter(now.minusSeconds(30))) {
            throw new ConsultationStateException("잠시 후 다시 시도해 주세요.");
        }
        job.retry(now);
    }
}
