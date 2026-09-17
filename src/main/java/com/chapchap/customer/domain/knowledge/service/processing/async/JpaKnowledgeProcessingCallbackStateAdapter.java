package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingCallbackOutcome;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallbackHeaders;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCompletedEvent;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingJobSnapshot;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingRetryRequestedEvent;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;
import com.chapchap.customer.global.exception.knowledge.KnowledgeProcessingStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "customer.ai.knowledge-processing", name = "async-enabled", havingValue = "true")
public class JpaKnowledgeProcessingCallbackStateAdapter implements KnowledgeProcessingCallbackStatePort {
    private static final Clock KST_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));
    private final KnowledgeProcessingJobRepository jobRepository;
    private final KnowledgeProcessingAttemptRepository attemptRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final AuditLogWriter auditLogWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final KnowledgeProcessingCallbackStateMachine stateMachine = new KnowledgeProcessingCallbackStateMachine();

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public KnowledgeProcessingCallbackOutcome applyAtomically(
            KnowledgeProcessingCallbackHeaders headers, KnowledgeProcessingCallback callback) {
        LocalDateTime now = LocalDateTime.now(KST_CLOCK);
        Long versionId = attemptRepository.findKnowledgeVersionIdByRequestId(headers.requestId()).orElse(null);
        if (versionId == null) {
            return jobRepository.existsByProcessingId(callback.processingId())
                    ? KnowledgeProcessingCallbackOutcome.CONFLICT
                    : KnowledgeProcessingCallbackOutcome.IGNORED_STALE;
        }
        // prepareAttempt/markAccepted/markSubmissionFailed와 같은 순서: Version 먼저.
        KnowledgeVersion version = knowledgeVersionRepository.findByIdForUpdate(versionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge Version을 찾을 수 없습니다."));
        KnowledgeProcessingJob job = jobRepository.findByKnowledgeVersionId(versionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge 처리 Job을 찾을 수 없습니다."));
        KnowledgeProcessingAttempt attempt = attemptRepository
                .findCurrentForUpdate(job.getId(), job.getCurrentAttempt())
                .orElseThrow(() -> new KnowledgeProcessingStateException("현재 Knowledge 처리 attempt를 찾을 수 없습니다."));
        if (!attempt.getRequestId().equals(headers.requestId())) {
            return KnowledgeProcessingCallbackOutcome.IGNORED_STALE;
        }
        if (version.getId() != callback.knowledgeVersionId()
                || (job.getProcessingId() != null && job.getProcessingId() != callback.processingId())) {
            return KnowledgeProcessingCallbackOutcome.CONFLICT;
        }
        if (job.getProcessingId() == null
                && jobRepository.existsByProcessingId(callback.processingId())) {
            return KnowledgeProcessingCallbackOutcome.CONFLICT;
        }
        // 202 응답 저장보다 callback이 먼저 와도 검증 가능하게 하되,
        // 검증에 실패한 callback의 processingId는 DB에 저장하지 않는다.
        long processingId = job.getProcessingId() == null ? callback.processingId() : job.getProcessingId();
        KnowledgeProcessingJobSnapshot snapshot = new KnowledgeProcessingJobSnapshot(
                attempt.getRequestId(), processingId, version.getId(), job.getCurrentAttempt(),
                job.getChunkProfile(), attempt.terminalCallback(processingId, version.getId()));
        KnowledgeProcessingCallbackOutcome outcome = stateMachine.decide(snapshot, headers, callback);
        if (outcome != KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED
                && outcome != KnowledgeProcessingCallbackOutcome.APPLIED_FAILED) {
            return outcome;
        }
        if (version.getProcessingStatus() != KnowledgeProcessingStatus.PROCESSING) {
            return KnowledgeProcessingCallbackOutcome.CONFLICT;
        }
        if (job.getProcessingId() == null) {
            job.bindProcessingId(processingId, now);
        }
        String fingerprint = KnowledgeProcessingCallbackFingerprint.create(callback);
        attempt.applyTerminal(callback, fingerprint, now);
        if (outcome == KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED) {
            job.applyCompleted(fingerprint, callback.chunkCount(), now);
            version.completeProcessing(now);
            eventPublisher.publishEvent(new KnowledgeProcessingCompletedEvent(version.getId()));
        } else {
            boolean retryable = Boolean.TRUE.equals(callback.retryable());
            job.applyFailed(fingerprint, callback.failureCode(), retryable, now);
            if (retryable && job.getCurrentAttempt() < KnowledgeVersion.MAX_PROCESSING_ATTEMPTS) {
                eventPublisher.publishEvent(new KnowledgeProcessingRetryRequestedEvent(version.getId(), job.getCurrentAttempt()));
            } else {
                version.failProcessing(callback.failureCode().name(), retryable, now);
                auditLogWriter.recordKnowledgeProcessingFailed(version, now);
            }
        }
        return outcome;
    }
}
