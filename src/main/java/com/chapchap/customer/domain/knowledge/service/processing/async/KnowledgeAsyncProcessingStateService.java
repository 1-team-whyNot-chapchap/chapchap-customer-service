package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingAttemptStatus;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingJobStatus;
import com.chapchap.customer.domain.knowledge.dto.processing.KnowledgeProcessingContext;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingRetryRequestedEvent;
import com.chapchap.customer.domain.knowledge.dto.processing.async.PreparedKnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;
import com.chapchap.customer.domain.knowledge.response.processing.async.CustomerAiKnowledgeJobAccepted;
import com.chapchap.customer.global.exception.knowledge.KnowledgeProcessingStateException;
import com.chapchap.customer.global.exception.knowledge.processing.async.CustomerAiKnowledgeJobClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "customer.ai.knowledge-processing", name = "async-enabled", havingValue = "true")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class KnowledgeAsyncProcessingStateService {
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final KnowledgeProcessingJobRepository jobRepository;
    private final KnowledgeProcessingAttemptRepository attemptRepository;
    private final AuditLogWriter auditLogWriter;
    private final ApplicationEventPublisher eventPublisher;

    // 기존 호출자와의 호환용. 실제 스케줄러는 아래 4인자 메서드로 재시도 번호까지 검증한다.
    public Optional<PreparedKnowledgeProcessingAttempt> prepareAttempt(
            Long knowledgeVersionId, UUID requestId, LocalDateTime now) {
        return prepareAttempt(knowledgeVersionId, requestId, now, null);
    }

    public Optional<PreparedKnowledgeProcessingAttempt> prepareAttempt(
            Long knowledgeVersionId, UUID requestId, LocalDateTime now,
            Integer expectedCompletedAttempt) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(now);
        if (expectedCompletedAttempt != null
                && (expectedCompletedAttempt < 0 || expectedCompletedAttempt > 2)) {
            throw new IllegalArgumentException("이전 완료 attempt는 0~2여야 합니다.");
        }

        // 최초/재시도/콜백 모두 존재하는 Version을 먼저 잠근다.
        KnowledgeVersion version = requireVersionForUpdate(knowledgeVersionId);
        if (version.getProcessingStatus() == KnowledgeProcessingStatus.READY
                || version.getProcessingStatus() == KnowledgeProcessingStatus.FAILED) {
            return Optional.empty();
        }

        // READ_COMMITTED + Version 잠금 뒤 조회. 없는 Job에 FOR UPDATE를 걸지 않는다.
        KnowledgeProcessingJob job = jobRepository.findByKnowledgeVersionId(knowledgeVersionId)
                .orElse(null);
        if (job == null) {
            if (version.getProcessingStatus() != KnowledgeProcessingStatus.UPLOADED
                    || version.getProcessingAttemptCount() != 0) {
                throw stateError("처리 중인 Version에 Job이 없습니다. 자동 복구하지 않고 상태 확인이 필요합니다.");
            }
            if (expectedCompletedAttempt != null && expectedCompletedAttempt != 0) {
                return Optional.empty();
            }
            version.startProcessing(now);
            job = jobRepository.saveAndFlush(KnowledgeProcessingJob.create(
                    version.getId(), version.getChunkProfile(), now));
        } else {
            if (job.getCurrentAttempt() != version.getProcessingAttemptCount()) {
                throw stateError("Version과 Job의 attempt 번호가 일치하지 않습니다.");
            }
            if (expectedCompletedAttempt != null
                    && expectedCompletedAttempt != job.getCurrentAttempt()) {
                return Optional.empty(); // 지연된 과거 재시도 이벤트 또는 중복 초기 이벤트
            }
            if (job.getStatus() != KnowledgeProcessingJobStatus.FAILED
                    || !job.isRetryable()
                    || job.getCurrentAttempt() >= KnowledgeVersion.MAX_PROCESSING_ATTEMPTS) {
                return Optional.empty(); // 진행 중 요청을 새 attempt로 덮어쓰지 않는다.
            }
            KnowledgeProcessingAttempt previous = attemptRepository
                    .findCurrentForUpdate(job.getId(), job.getCurrentAttempt())
                    .orElseThrow(() -> stateError("이전 Knowledge 처리 attempt를 찾을 수 없습니다."));
            if (previous.getStatus() != KnowledgeProcessingAttemptStatus.TERMINAL
                    && previous.getStatus() != KnowledgeProcessingAttemptStatus.SUBMISSION_FAILED) {
                throw stateError("아직 종료되지 않은 attempt는 재시도할 수 없습니다.");
            }
            previous.markSuperseded(now);
            version.startProcessing(now);
            job.beginAttempt(version.getProcessingAttemptCount(), now);
        }

        KnowledgeDocument document = knowledgeDocumentRepository.findById(version.getKnowledgeDocumentId())
                .orElseThrow(() -> stateError("Knowledge 문서를 찾을 수 없습니다."));
        attemptRepository.save(KnowledgeProcessingAttempt.create(
                job.getId(), version.getProcessingAttemptCount(), requestId, now));
        return Optional.of(new PreparedKnowledgeProcessingAttempt(requestId, new KnowledgeProcessingContext(
                version.getId(), version.getProcessingAttemptCount(), version.getObjectKey(),
                version.getContentType(), version.getFileSize(), document.getDocumentKey(),
                document.getSourceService(), document.getCategory(), version.getVersion(),
                version.getEffectiveFrom(), version.getChunkProfile())));
    }

    public void markSubmitted(UUID requestId, LocalDateTime now) {
        LockedAttempt locked = lockCurrentAttempt(requestId).orElse(null);
        if (locked == null || locked.attempt().getStatus() != KnowledgeProcessingAttemptStatus.CREATED) {
            return;
        }
        locked.attempt().markSubmitted(now);
    }

    public void markAccepted(UUID requestId, CustomerAiKnowledgeJobAccepted accepted, LocalDateTime now) {
        LockedAttempt locked = lockCurrentAttempt(requestId).orElse(null);
        if (locked == null) {
            return; // 이미 다음 attempt로 넘어간 요청의 늦은 HTTP 응답
        }
        if (locked.job().getKnowledgeVersionId() != accepted.knowledgeVersionId()) {
            throw stateError("Customer-AI 수락 응답의 Knowledge Version이 충돌합니다.");
        }
        Long processingId = locked.job().getProcessingId();
        if (processingId != null && processingId != accepted.processingId()) {
            throw stateError("Customer-AI 수락 응답의 processingId가 충돌합니다.");
        }
        if (locked.attempt().getStatus() == KnowledgeProcessingAttemptStatus.TERMINAL
                || locked.attempt().getStatus() == KnowledgeProcessingAttemptStatus.SUBMISSION_FAILED) {
            return; // callback이 먼저 끝낸 상태를 ACCEPTED로 되돌리지 않는다.
        }
        locked.job().bindProcessingId(accepted.processingId(), now);
        locked.attempt().markAccepted(now);
    }

    public void markSubmissionFailed(UUID requestId,
            CustomerAiKnowledgeJobClientException.Reason reason,
            boolean retryable, LocalDateTime now) {
        LockedAttempt locked = lockCurrentAttempt(requestId).orElse(null);
        if (locked == null
                || locked.attempt().getStatus() == KnowledgeProcessingAttemptStatus.TERMINAL
                || locked.attempt().getStatus() == KnowledgeProcessingAttemptStatus.SUBMISSION_FAILED
                || locked.version().getProcessingStatus() == KnowledgeProcessingStatus.READY
                || locked.version().getProcessingStatus() == KnowledgeProcessingStatus.FAILED) {
            return;
        }
        locked.attempt().markSubmissionFailed(now);
        locked.job().markSubmissionFailed(reason.name(), retryable, now);
        if (retryable && locked.job().getCurrentAttempt() < KnowledgeVersion.MAX_PROCESSING_ATTEMPTS) {
            eventPublisher.publishEvent(new KnowledgeProcessingRetryRequestedEvent(
                    locked.version().getId(), locked.job().getCurrentAttempt()));
        } else {
            locked.version().failProcessing(reason.name(), retryable, now);
            auditLogWriter.recordKnowledgeProcessingFailed(locked.version(), now);
        }
    }

    private Optional<LockedAttempt> lockCurrentAttempt(UUID requestId) {
        // Entity를 미리 읽지 않아, 락을 기다리는 동안 다른 요청이 바꾼 상태를 캐시하지 않는다.
        Long versionId = attemptRepository.findKnowledgeVersionIdByRequestId(requestId)
                .orElseThrow(() -> stateError("Knowledge 처리 attempt를 찾을 수 없습니다."));
        KnowledgeVersion version = requireVersionForUpdate(versionId);
        KnowledgeProcessingJob job = jobRepository.findByKnowledgeVersionId(versionId)
                .orElseThrow(() -> stateError("Knowledge 처리 Job을 찾을 수 없습니다."));
        KnowledgeProcessingAttempt attempt = attemptRepository
                .findCurrentForUpdate(job.getId(), job.getCurrentAttempt())
                .orElseThrow(() -> stateError("현재 Knowledge 처리 attempt를 찾을 수 없습니다."));
        if (!attempt.getRequestId().equals(requestId)) {
            return Optional.empty();
        }
        return Optional.of(new LockedAttempt(version, job, attempt));
    }

    private KnowledgeVersion requireVersionForUpdate(Long versionId) {
        return knowledgeVersionRepository.findByIdForUpdate(versionId)
                .orElseThrow(() -> stateError("Knowledge Version을 찾을 수 없습니다."));
    }

    private KnowledgeProcessingStateException stateError(String message) {
        return new KnowledgeProcessingStateException(message);
    }

    private record LockedAttempt(KnowledgeVersion version,
            KnowledgeProcessingJob job, KnowledgeProcessingAttempt attempt) { }
}
