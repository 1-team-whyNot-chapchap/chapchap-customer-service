package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingCallbackOutcome;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingJobStatus;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallbackHeaders;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCompletedEvent;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingRetryRequestedEvent;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaKnowledgeProcessingCallbackStateAdapterTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private KnowledgeProcessingJobRepository jobRepository;
    @Mock
    private KnowledgeProcessingAttemptRepository attemptRepository;
    @Mock
    private KnowledgeVersionRepository versionRepository;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private KnowledgeVersion version;

    private JpaKnowledgeProcessingCallbackStateAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaKnowledgeProcessingCallbackStateAdapter(
                jobRepository,
                attemptRepository,
                versionRepository,
                auditLogWriter,
                eventPublisher
        );
    }

    @Test
    void appliesCompletedCallbackAndTreatsSameTerminalAsDuplicate() {
        KnowledgeProcessingJob job = job(REQUEST_ID, 1, 8001L);
        KnowledgeProcessingAttempt attempt = attempt(job, 1, REQUEST_ID);
        KnowledgeProcessingCallback callback = completed();
        stubCurrent(job, attempt, version);

        assertThat(adapter.applyAtomically(headers(REQUEST_ID), callback))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED);
        assertThat(adapter.applyAtomically(headers(REQUEST_ID), callback))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE);

        assertThat(job.getStatus()).isEqualTo(KnowledgeProcessingJobStatus.COMPLETED);
        verify(version).completeProcessing(any(LocalDateTime.class));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(KnowledgeProcessingCompletedEvent.class);
    }

    @Test
    void ignoresCallbackFromPreviousAttempt() {
        UUID currentRequestId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        KnowledgeProcessingJob job = job(REQUEST_ID, 1, 8001L);
        KnowledgeProcessingAttempt first = attempt(job, 1, REQUEST_ID);
        first.markSuperseded(LocalDateTime.now());
        job.beginAttempt(2, LocalDateTime.now());
        KnowledgeProcessingAttempt current = attempt(job, 2, currentRequestId);

        when(jobRepository.findByProcessingIdForUpdate(8001L)).thenReturn(Optional.of(job));
        when(attemptRepository.findCurrentForUpdate(job.getId(), 2)).thenReturn(Optional.of(current));
        when(attemptRepository.existsByKnowledgeProcessingJobIdAndRequestId(job.getId(), REQUEST_ID))
                .thenReturn(true);

        assertThat(adapter.applyAtomically(headers(REQUEST_ID), completed()))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.IGNORED_STALE);
        verify(versionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void bindsProcessingIdWhenCallbackArrivesBeforeAcceptedResponseIsSaved() {
        KnowledgeProcessingJob job = job(REQUEST_ID, 1, null);
        KnowledgeProcessingAttempt attempt = attempt(job, 1, REQUEST_ID);
        when(jobRepository.findByProcessingIdForUpdate(8001L)).thenReturn(Optional.empty());
        when(attemptRepository.findByRequestId(REQUEST_ID)).thenReturn(Optional.of(attempt));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(attemptRepository.findCurrentForUpdate(job.getId(), 1)).thenReturn(Optional.of(attempt));
        when(versionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(version));
        when(version.getId()).thenReturn(101L);

        assertThat(adapter.applyAtomically(headers(REQUEST_ID), completed()))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED);
        assertThat(job.getProcessingId()).isEqualTo(8001L);
    }

    @Test
    void rejectsDifferentProcessingIdForKnownRequest() {
        KnowledgeProcessingJob job = job(REQUEST_ID, 1, 9001L);
        KnowledgeProcessingAttempt attempt = attempt(job, 1, REQUEST_ID);
        when(jobRepository.findByProcessingIdForUpdate(8001L)).thenReturn(Optional.empty());
        when(attemptRepository.findByRequestId(REQUEST_ID)).thenReturn(Optional.of(attempt));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThat(adapter.applyAtomically(headers(REQUEST_ID), completed()))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.CONFLICT);
    }

    @Test
    void requestsRetryWithoutFailingVersionBeforeMaximumAttempt() {
        KnowledgeProcessingJob job = job(REQUEST_ID, 1, 8001L);
        KnowledgeProcessingAttempt attempt = attempt(job, 1, REQUEST_ID);
        stubCurrent(job, attempt, version);
        KnowledgeProcessingCallback failed = new KnowledgeProcessingCallback(
                8001L,
                101L,
                KnowledgeProcessingCallback.Status.FAILED,
                null,
                null,
                KnowledgeProcessingCallback.FailureCode.VECTOR_STORE_UNAVAILABLE,
                true
        );

        assertThat(adapter.applyAtomically(headers(REQUEST_ID), failed))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.APPLIED_FAILED);
        verify(version, never()).failProcessing(any(), any(Boolean.class), any(LocalDateTime.class));
        verify(eventPublisher).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
    }

    @Test
    void recordsFinalFailureAtMaximumAttemptWithoutAnotherRetry() {
        KnowledgeProcessingJob job = job(REQUEST_ID, 3, 8001L);
        KnowledgeProcessingAttempt attempt = attempt(job, 3, REQUEST_ID);
        stubCurrent(job, attempt, version);
        KnowledgeProcessingCallback failed = new KnowledgeProcessingCallback(
                8001L,
                101L,
                KnowledgeProcessingCallback.Status.FAILED,
                null,
                null,
                KnowledgeProcessingCallback.FailureCode.PROCESSING_TIMEOUT,
                true
        );

        assertThat(adapter.applyAtomically(headers(REQUEST_ID), failed))
                .isEqualTo(KnowledgeProcessingCallbackOutcome.APPLIED_FAILED);
        verify(version).failProcessing(
                org.mockito.ArgumentMatchers.eq("PROCESSING_TIMEOUT"),
                org.mockito.ArgumentMatchers.eq(true),
                any(LocalDateTime.class));
        verify(auditLogWriter).recordKnowledgeProcessingFailed(
                org.mockito.ArgumentMatchers.eq(version), any(LocalDateTime.class));
        verify(eventPublisher, never()).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
    }

    private void stubCurrent(
            KnowledgeProcessingJob job,
            KnowledgeProcessingAttempt attempt,
            KnowledgeVersion storedVersion
    ) {
        when(jobRepository.findByProcessingIdForUpdate(8001L)).thenReturn(Optional.of(job));
        when(attemptRepository.findCurrentForUpdate(job.getId(), job.getCurrentAttempt()))
                .thenReturn(Optional.of(attempt));
        when(versionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(storedVersion));
        org.mockito.Mockito.lenient().when(storedVersion.getId()).thenReturn(101L);
    }

    private KnowledgeProcessingJob job(UUID requestId, int attemptNumber, Long processingId) {
        KnowledgeProcessingJob job = KnowledgeProcessingJob.create(
                101L, "HYBRID_POLICY_V1", LocalDateTime.now());
        ReflectionTestUtils.setField(job, "id", 1L);
        for (int nextAttempt = 2; nextAttempt <= attemptNumber; nextAttempt++) {
            job.beginAttempt(nextAttempt, LocalDateTime.now());
        }
        if (processingId != null) {
            job.bindProcessingId(processingId, LocalDateTime.now());
        }
        return job;
    }

    private KnowledgeProcessingAttempt attempt(
            KnowledgeProcessingJob job,
            int attemptNumber,
            UUID requestId
    ) {
        return KnowledgeProcessingAttempt.create(job.getId(), attemptNumber, requestId, LocalDateTime.now());
    }

    private KnowledgeProcessingCallbackHeaders headers(UUID requestId) {
        return new KnowledgeProcessingCallbackHeaders(requestId, 8001L);
    }

    private KnowledgeProcessingCallback completed() {
        return new KnowledgeProcessingCallback(
                8001L,
                101L,
                KnowledgeProcessingCallback.Status.COMPLETED,
                24,
                "HYBRID_POLICY_V1",
                null,
                null
        );
    }
}
