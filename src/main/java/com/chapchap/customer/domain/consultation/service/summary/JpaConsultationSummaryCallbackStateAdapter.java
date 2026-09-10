package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryCallbackOutcome;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallbackHeaders;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryJobSnapshot;
import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummary;
import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummaryJob;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryJobRepository;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryRepository;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.constant.ConsultationStatus;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.global.exception.consultation.ConsultationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.consultation-summary",
        name = "async-enabled",
        havingValue = "true"
)
public class JpaConsultationSummaryCallbackStateAdapter implements ConsultationSummaryCallbackStatePort {
    private static final Clock KST_CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    private final ConsultationSummaryJobRepository jobRepository;
    private final ConsultationSummaryRepository summaryRepository;
    private final ConsultationRepository consultationRepository;
    private final AuditLogWriter auditLogWriter;
    private final ConsultationSummaryCallbackStateMachine stateMachine =
            new ConsultationSummaryCallbackStateMachine();

    @Override
    @Transactional
    public ConsultationSummaryCallbackOutcome applyAtomically(
            ConsultationSummaryCallbackHeaders headers,
            ConsultationSummaryCallback callback
    ) {
        ConsultationSummaryJob job = jobRepository.findByIdForUpdate(callback.summaryJobId())
                .orElse(null);
        if (job == null) {
            return ConsultationSummaryCallbackOutcome.IGNORED_STALE;
        }
        if (!job.getRequestId().equals(headers.requestId())
                || job.getConsultationId() != callback.consultationId()) {
            return ConsultationSummaryCallbackOutcome.CONFLICT;
        }

        String fingerprint = ConsultationSummaryCallbackFingerprint.create(callback);
        if (job.isTerminal()) {
            return job.getTerminalFingerprint().equals(fingerprint)
                    ? ConsultationSummaryCallbackOutcome.IGNORED_DUPLICATE
                    : ConsultationSummaryCallbackOutcome.CONFLICT;
        }

        Consultation consultation = consultationRepository.findByIdForMessageWrite(job.getConsultationId())
                .orElseThrow(ConsultationNotFoundException::new);
        if (consultation.getStatus() != ConsultationStatus.CLOSED) {
            return ConsultationSummaryCallbackOutcome.CONFLICT;
        }
        ConsultationSummaryJobSnapshot snapshot = new ConsultationSummaryJobSnapshot(
                job.getRequestId(),
                job.getId(),
                job.getConsultationId(),
                consultation.getStatus(),
                null
        );
        ConsultationSummaryCallbackOutcome outcome = stateMachine.decide(snapshot, headers, callback);
        if (outcome == ConsultationSummaryCallbackOutcome.CONFLICT) {
            return ConsultationSummaryCallbackOutcome.CONFLICT;
        }

        LocalDateTime now = LocalDateTime.now(KST_CLOCK);
        if (outcome == ConsultationSummaryCallbackOutcome.APPLIED_COMPLETED) {
            if (summaryRepository.findByConsultationIdForUpdate(job.getConsultationId()).isPresent()) {
                return ConsultationSummaryCallbackOutcome.CONFLICT;
            }
            summaryRepository.save(ConsultationSummary.fromAi(
                    job.getConsultationId(), callback.summary(), now));
            job.applyCompleted(fingerprint, now);
            auditLogWriter.recordConsultationSummaryResult(
                    job.getConsultationId(), job.getId(), true, null, now);
            return outcome;
        }

        job.applyFailed(
                fingerprint,
                callback.failureCode(),
                Boolean.TRUE.equals(callback.retryable()),
                now
        );
        auditLogWriter.recordConsultationSummaryResult(
                job.getConsultationId(),
                job.getId(),
                false,
                callback.failureCode().name(),
                now
        );
        return outcome;
    }
}
