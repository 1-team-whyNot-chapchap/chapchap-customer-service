package com.chapchap.customer.domain.knowledge.processing.async;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.processing.KnowledgeProcessingContext;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.global.error.custom.knowledge.KnowledgeProcessingStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.knowledge-processing",
        name = "async-enabled",
        havingValue = "true"
)
public class KnowledgeAsyncProcessingStateService {
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final KnowledgeProcessingJobRepository jobRepository;
    private final KnowledgeProcessingAttemptRepository attemptRepository;
    private final AuditLogWriter auditLogWriter;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Optional<PreparedKnowledgeProcessingAttempt> prepareAttempt(
            Long knowledgeVersionId,
            UUID requestId,
            LocalDateTime now
    ) {
        KnowledgeVersion version = knowledgeVersionRepository.findByIdForUpdate(knowledgeVersionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "Knowledge Version을 찾을 수 없습니다."));
        if (version.getProcessingStatus()
                == com.chapchap.customer.domain.knowledge.entity.KnowledgeProcessingStatus.READY
                || version.getProcessingStatus()
                == com.chapchap.customer.domain.knowledge.entity.KnowledgeProcessingStatus.FAILED) {
            return Optional.empty();
        }
        KnowledgeDocument document = knowledgeDocumentRepository.findById(version.getKnowledgeDocumentId())
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "Knowledge 문서를 찾을 수 없습니다."));

        version.startProcessing(now);
        int attemptNumber = version.getProcessingAttemptCount();
        KnowledgeProcessingJob job = jobRepository
                .findByKnowledgeVersionIdForUpdate(knowledgeVersionId)
                .orElseGet(() -> jobRepository.saveAndFlush(KnowledgeProcessingJob.create(
                        knowledgeVersionId, version.getChunkProfile(), now)));

        if (attemptNumber > 1) {
            KnowledgeProcessingAttempt previousAttempt = attemptRepository
                    .findCurrentForUpdate(job.getId(), job.getCurrentAttempt())
                    .orElseThrow(() -> new KnowledgeProcessingStateException(
                            "이전 Knowledge 처리 attempt를 찾을 수 없습니다."));
            previousAttempt.markSuperseded(now);
            job.beginAttempt(attemptNumber, now);
        } else if (job.getCurrentAttempt() != 1) {
            throw new KnowledgeProcessingStateException("Knowledge 처리 Job attempt가 충돌합니다.");
        }

        attemptRepository.save(KnowledgeProcessingAttempt.create(
                job.getId(), attemptNumber, requestId, now));
        return Optional.of(new PreparedKnowledgeProcessingAttempt(
                requestId,
                new KnowledgeProcessingContext(
                        version.getId(),
                        attemptNumber,
                        version.getObjectKey(),
                        version.getContentType(),
                        version.getFileSize(),
                        document.getDocumentKey(),
                        document.getSourceService(),
                        document.getCategory(),
                        version.getVersion(),
                        version.getEffectiveFrom(),
                        version.getChunkProfile()
                )
        ));
    }

    @Transactional
    public void markSubmitted(UUID requestId, LocalDateTime now) {
        KnowledgeProcessingAttempt attempt = requireAttempt(requestId);
        lockCurrentJob(attempt);
        attempt.markSubmitted(now);
    }

    @Transactional
    public void markAccepted(
            UUID requestId,
            CustomerAiKnowledgeJobAccepted accepted,
            LocalDateTime now
    ) {
        KnowledgeProcessingAttempt attempt = requireAttempt(requestId);
        KnowledgeProcessingJob job = lockCurrentJob(attempt);
        if (job.getKnowledgeVersionId() != accepted.knowledgeVersionId()) {
            throw new KnowledgeProcessingStateException("Customer-AI 수락 응답의 Knowledge Version이 충돌합니다.");
        }
        job.bindProcessingId(accepted.processingId(), now);
        attempt.markAccepted(now);
    }

    @Transactional
    public void markSubmissionFailed(
            UUID requestId,
            CustomerAiKnowledgeJobClientException.Reason reason,
            boolean retryable,
            LocalDateTime now
    ) {
        KnowledgeProcessingAttempt attempt = requireAttempt(requestId);
        KnowledgeProcessingJob job = lockCurrentJob(attempt);
        if (attempt.getStatus() == KnowledgeProcessingAttemptStatus.TERMINAL) {
            return;
        }
        attempt.markSubmissionFailed(now);
        job.markSubmissionFailed(reason.name(), retryable, now);

        KnowledgeVersion version = knowledgeVersionRepository
                .findByIdForUpdate(job.getKnowledgeVersionId())
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "Knowledge Version을 찾을 수 없습니다."));
        if (retryable && job.getCurrentAttempt() < 3) {
            eventPublisher.publishEvent(new KnowledgeProcessingRetryRequestedEvent(
                    version.getId(), job.getCurrentAttempt()));
            return;
        }
        version.failProcessing(reason.name(), retryable, now);
        auditLogWriter.recordKnowledgeProcessingFailed(version, now);
    }

    private KnowledgeProcessingAttempt requireAttempt(UUID requestId) {
        return attemptRepository.findByRequestId(requestId)
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "Knowledge 처리 attempt를 찾을 수 없습니다."));
    }

    private KnowledgeProcessingJob lockCurrentJob(KnowledgeProcessingAttempt attempt) {
        KnowledgeProcessingJob job = jobRepository.findByIdForUpdate(attempt.getKnowledgeProcessingJobId())
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "Knowledge 처리 Job을 찾을 수 없습니다."));
        if (job.getCurrentAttempt() != attempt.getAttempt()) {
            throw new KnowledgeProcessingStateException("현재 Knowledge 처리 attempt가 아닙니다.");
        }
        return job;
    }
}
