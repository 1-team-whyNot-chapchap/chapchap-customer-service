package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingAttemptStatus;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingRetryRequestedEvent;
import com.chapchap.customer.domain.knowledge.dto.processing.async.PreparedKnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.global.exception.knowledge.processing.async.CustomerAiKnowledgeJobClientException;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;
import com.chapchap.customer.domain.knowledge.response.processing.async.CustomerAiKnowledgeJobAccepted;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.dto.processing.KnowledgeProcessingContext;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.global.exception.knowledge.KnowledgeProcessingStateException;
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
        /*
         * Knowledge Processing Lock Policy
         *
         * 1. 기존 Job이 존재하는 경우
         *    Job -> Version 순서로 Lock
         *
         *    Callback 처리도 Job -> Version 순서를 사용하므로
         *    동일한 Lock 순서를 유지한다.
         *
         * 2. 최초 처리처럼 Job이 존재하지 않는 경우
         *    존재하지 않는 Job에 PESSIMISTIC_WRITE를 걸지 않는다.
         *
         *    대신 반드시 존재하는 KnowledgeVersion을 먼저 Lock하고,
         *    Lock 획득 후 Job 존재 여부를 다시 확인한다.
         *
         *    이를 통해 동시에 최초 처리 요청이 들어와도
         *    KnowledgeVersion Lock을 기준으로 Job 생성을 직렬화한다.
         */

        Optional<KnowledgeProcessingJob> existingJob =
            jobRepository.findByKnowledgeVersionId(knowledgeVersionId);

        KnowledgeProcessingJob job;
        KnowledgeVersion version;

        if (existingJob.isPresent()) {
            /*
             * 이미 Job이 존재하는 경우
             *
             * Callback과 동일하게:
             *
             * Job
             *  ↓
             * Version
             */

            job = jobRepository
                      .findByIdForUpdate(existingJob.get().getId())
                      .orElseThrow(() -> new KnowledgeProcessingStateException(
                          "Knowledge 처리 Job을 찾을 수 없습니다."
                      ));

            version = knowledgeVersionRepository
                          .findByIdForUpdate(knowledgeVersionId)
                          .orElseThrow(() -> new KnowledgeProcessingStateException(
                              "Knowledge Version을 찾을 수 없습니다."
                          ));

        } else {
            /*
             * 최초 처리
             *
             * 아직 Job이 없기 때문에
             * Job SELECT FOR UPDATE를 사용하지 않는다.
             *
             * Version을 mutex처럼 사용한다.
             */

            version = knowledgeVersionRepository
                          .findByIdForUpdate(knowledgeVersionId)
                          .orElseThrow(() -> new KnowledgeProcessingStateException(
                              "Knowledge Version을 찾을 수 없습니다."
                          ));

            /*
             * Version Lock을 기다리는 동안
             * 다른 Thread가 Job을 생성했을 수도 있으므로
             * 반드시 다시 조회한다.
             */
            job = jobRepository
                      .findByKnowledgeVersionId(knowledgeVersionId)
                      .orElseGet(() ->
                                     jobRepository.saveAndFlush(
                                         KnowledgeProcessingJob.create(
                                             knowledgeVersionId,
                                             version.getChunkProfile(),
                                             now
                                         )
                                     )
                      );
        }

        /*
         * 이미 처리가 끝난 Version이면
         * 중복 처리하지 않는다.
         */
        if (version.getProcessingStatus()
                == com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus.READY
                || version.getProcessingStatus()
                       == com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus.FAILED) {
            return Optional.empty();
        }

        KnowledgeDocument document = knowledgeDocumentRepository
                                         .findById(version.getKnowledgeDocumentId())
                                         .orElseThrow(() -> new KnowledgeProcessingStateException(
                                             "Knowledge 문서를 찾을 수 없습니다."
                                         ));

        /*
         * UPLOADED -> PROCESSING
         * 또는 Retry attempt 증가
         */
        version.startProcessing(now);

        int attemptNumber = version.getProcessingAttemptCount();

        /*
         * Retry 처리
         */
        if (attemptNumber > 1) {
            KnowledgeProcessingAttempt previousAttempt = attemptRepository
                                                             .findCurrentForUpdate(
                                                                 job.getId(),
                                                                 job.getCurrentAttempt()
                                                             )
                                                             .orElseThrow(() -> new KnowledgeProcessingStateException(
                                                                 "이전 Knowledge 처리 attempt를 찾을 수 없습니다."
                                                             ));

            previousAttempt.markSuperseded(now);
            job.beginAttempt(attemptNumber, now);

        } else if (job.getCurrentAttempt() != 1) {
            throw new KnowledgeProcessingStateException(
                "Knowledge 처리 Job attempt가 충돌합니다."
            );
        }

        /*
         * 이번 처리 Attempt 생성
         */
        attemptRepository.save(
            KnowledgeProcessingAttempt.create(
                job.getId(),
                attemptNumber,
                requestId,
                now
            )
        );

        return Optional.of(
            new PreparedKnowledgeProcessingAttempt(
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
            )
        );
    }

    @Transactional
    public void markSubmitted(
            UUID requestId,
            LocalDateTime now
    ) {
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
            throw new KnowledgeProcessingStateException(
                    "Customer-AI 수락 응답의 Knowledge Version이 충돌합니다.");
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
            eventPublisher.publishEvent(
                    new KnowledgeProcessingRetryRequestedEvent(
                            version.getId(),
                            job.getCurrentAttempt()
                    )
            );
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

    private KnowledgeProcessingJob lockCurrentJob(
            KnowledgeProcessingAttempt attempt
    ) {
        KnowledgeProcessingJob job = jobRepository
                .findByIdForUpdate(attempt.getKnowledgeProcessingJobId())
                .orElseThrow(() -> new KnowledgeProcessingStateException(
                        "Knowledge 처리 Job을 찾을 수 없습니다."));

        if (job.getCurrentAttempt() != attempt.getAttempt()) {
            throw new KnowledgeProcessingStateException(
                    "현재 Knowledge 처리 attempt가 아닙니다.");
        }

        return job;
    }
}