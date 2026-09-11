package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingCallbackOutcome;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallbackHeaders;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCompletedEvent;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingJobSnapshot;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingRetryRequestedEvent;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.global.exception.knowledge.KnowledgeProcessingStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.knowledge-processing",
        name = "async-enabled",
        havingValue = "true"
)
public class JpaKnowledgeProcessingCallbackStateAdapter implements KnowledgeProcessingCallbackStatePort {
    private static final Clock KST_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final KnowledgeProcessingJobRepository jobRepository;
    private final KnowledgeProcessingAttemptRepository attemptRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final AuditLogWriter auditLogWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final KnowledgeProcessingCallbackStateMachine stateMachine =
            new KnowledgeProcessingCallbackStateMachine();

    @Override
    @Transactional
    public KnowledgeProcessingCallbackOutcome applyAtomically(
            KnowledgeProcessingCallbackHeaders headers,
            KnowledgeProcessingCallback callback
    ) {
        LocalDateTime now = LocalDateTime.now(KST_CLOCK);
        JobResolution resolution = resolveAndLockJob(headers, callback, now);
        if (resolution.outcome() != null) {
            return resolution.outcome();
        }
        KnowledgeProcessingJob job = resolution.job();

        KnowledgeProcessingAttempt currentAttempt = attemptRepository
                .findCurrentForUpdate(job.getId(), job.getCurrentAttempt())
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "현재 Knowledge 처리 attempt를 찾을 수 없습니다."));

        if (!currentAttempt.getRequestId().equals(headers.requestId())) {
            return attemptRepository.existsByKnowledgeProcessingJobIdAndRequestId(
                    job.getId(), headers.requestId())
                    ? KnowledgeProcessingCallbackOutcome.IGNORED_STALE
                    : KnowledgeProcessingCallbackOutcome.CONFLICT;
        }

        KnowledgeProcessingJobSnapshot snapshot = new KnowledgeProcessingJobSnapshot(
                currentAttempt.getRequestId(),
                job.getProcessingId(),
                job.getKnowledgeVersionId(),
                job.getCurrentAttempt(),
                job.getChunkProfile(),
                currentAttempt.terminalCallback(job.getProcessingId(), job.getKnowledgeVersionId())
        );
        KnowledgeProcessingCallbackOutcome outcome = stateMachine.decide(snapshot, headers, callback);
        if (outcome != KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED
                && outcome != KnowledgeProcessingCallbackOutcome.APPLIED_FAILED) {
            return outcome;
        }

        KnowledgeVersion knowledgeVersion = knowledgeVersionRepository
                .findByIdForUpdate(job.getKnowledgeVersionId())
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "Knowledge Version을 찾을 수 없습니다."));
        String fingerprint = KnowledgeProcessingCallbackFingerprint.create(callback);
        currentAttempt.applyTerminal(callback, fingerprint, now);

        if (outcome == KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED) {
            job.applyCompleted(fingerprint, callback.chunkCount(), now);
            knowledgeVersion.completeProcessing(now);
            eventPublisher.publishEvent(new KnowledgeProcessingCompletedEvent(knowledgeVersion.getId()));
            return outcome;
        }

        boolean retryable = Boolean.TRUE.equals(callback.retryable());
        job.applyFailed(fingerprint, callback.failureCode(), retryable, now);
        if (retryable && job.getCurrentAttempt() < 3) {
            eventPublisher.publishEvent(new KnowledgeProcessingRetryRequestedEvent(
                    knowledgeVersion.getId(), job.getCurrentAttempt()));
        } else {
            knowledgeVersion.failProcessing(callback.failureCode().name(), retryable, now);
            auditLogWriter.recordKnowledgeProcessingFailed(knowledgeVersion, now);
        }
        return outcome;
    }

    private JobResolution resolveAndLockJob(
            KnowledgeProcessingCallbackHeaders headers,
            KnowledgeProcessingCallback callback,
            LocalDateTime now
    ) {
        Optional<KnowledgeProcessingJob> byProcessingId = jobRepository
                .findByProcessingIdForUpdate(callback.processingId());
        if (byProcessingId.isPresent()) {
            return JobResolution.found(byProcessingId.get());
        }

        Optional<KnowledgeProcessingAttempt> byRequestId = attemptRepository
                .findByRequestId(headers.requestId());
        if (byRequestId.isEmpty()) {
            return JobResolution.finished(KnowledgeProcessingCallbackOutcome.IGNORED_STALE);
        }
        KnowledgeProcessingJob job = jobRepository
                .findByIdForUpdate(byRequestId.get().getKnowledgeProcessingJobId())
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "Knowledge 처리 Job을 찾을 수 없습니다."));
        if (job.getProcessingId() != null && job.getProcessingId() != callback.processingId()) {
            return JobResolution.finished(KnowledgeProcessingCallbackOutcome.CONFLICT);
        }
        job.bindProcessingId(callback.processingId(), now);
        return JobResolution.found(job);
    }

    private record JobResolution(
            KnowledgeProcessingJob job,
            KnowledgeProcessingCallbackOutcome outcome
    ) {
        private static JobResolution found(KnowledgeProcessingJob job) {
            return new JobResolution(job, null);
        }

        private static JobResolution finished(KnowledgeProcessingCallbackOutcome outcome) {
            return new JobResolution(null, outcome);
        }
    }
}
