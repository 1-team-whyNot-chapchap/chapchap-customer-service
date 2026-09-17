package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingCallbackOutcome;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingJobStatus;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallbackHeaders;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCompletedEvent;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingRetryRequestedEvent;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JpaKnowledgeProcessingCallbackStateAdapterTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 12, 0);
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private KnowledgeProcessingJobRepository jobs;
    private KnowledgeProcessingAttemptRepository attempts;
    private KnowledgeVersionRepository versions;
    private AuditLogWriter audit;
    private ApplicationEventPublisher events;
    private JpaKnowledgeProcessingCallbackStateAdapter adapter;
    private KnowledgeVersion version;
    private KnowledgeProcessingJob job;
    private KnowledgeProcessingAttempt current;

    @BeforeEach
    void setUp() {
        jobs = mock(KnowledgeProcessingJobRepository.class);
        attempts = mock(KnowledgeProcessingAttemptRepository.class);
        versions = mock(KnowledgeVersionRepository.class);
        audit = mock(AuditLogWriter.class);
        events = mock(ApplicationEventPublisher.class);
        adapter = new JpaKnowledgeProcessingCallbackStateAdapter(jobs, attempts, versions, audit, events);
        version = KnowledgeVersion.uploaded(10L, "v1", "knowledge/source", "source.md", "text/markdown",
                100L, NOW, 1L, NOW);
        ReflectionTestUtils.setField(version, "id", 101L);
        version.startProcessing(NOW);
        job = KnowledgeProcessingJob.create(101L, "HYBRID_POLICY_V1", NOW);
        ReflectionTestUtils.setField(job, "id", 1L);
        current = KnowledgeProcessingAttempt.create(1L, 1, REQUEST_ID, NOW);
        when(attempts.findKnowledgeVersionIdByRequestId(REQUEST_ID)).thenReturn(Optional.of(101L));
        when(versions.findByIdForUpdate(101L)).thenReturn(Optional.of(version));
        when(jobs.findByKnowledgeVersionId(101L)).thenReturn(Optional.of(job));
        when(attempts.findCurrentForUpdate(eq(1L), anyInt())).thenAnswer(i -> Optional.of(current));
    }

    @Test
    void appliesCompletedCallbackAndTreatsSameTerminalAsDuplicate() {
        job.bindProcessingId(8001L, NOW);
        assertEquals(KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED, apply(completed()));
        assertEquals(KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE, apply(completed()));
        assertEquals(KnowledgeProcessingJobStatus.COMPLETED, job.getStatus());
        assertEquals(KnowledgeProcessingStatus.READY, version.getProcessingStatus());
        verify(events, times(1)).publishEvent(any(KnowledgeProcessingCompletedEvent.class));
        var order = inOrder(versions, jobs);
        order.verify(versions).findByIdForUpdate(101L);
        order.verify(jobs).findByKnowledgeVersionId(101L);
        verify(jobs, never()).findByProcessingIdForUpdate(anyLong());
    }

    @Test
    void ignoresCallbackFromPreviousAttempt() {
        job.bindProcessingId(8001L, NOW);
        job.beginAttempt(2, NOW);
        current = KnowledgeProcessingAttempt.create(1L, 2, UUID.randomUUID(), NOW);
        assertEquals(KnowledgeProcessingCallbackOutcome.IGNORED_STALE, apply(completed()));
        assertEquals(KnowledgeProcessingStatus.PROCESSING, version.getProcessingStatus());
        verifyNoInteractions(events);
    }

    @Test
    void bindsProcessingIdWhenCallbackArrivesBeforeAcceptedResponseIsSaved() {
        assertNull(job.getProcessingId());
        assertEquals(KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED, apply(completed()));
        assertEquals(8001L, job.getProcessingId());
    }

    @Test
    void rejectsDifferentProcessingIdForKnownRequest() {
        job.bindProcessingId(9001L, NOW);
        assertEquals(KnowledgeProcessingCallbackOutcome.CONFLICT, apply(completed()));
        assertEquals(9001L, job.getProcessingId());
        verifyNoInteractions(events);
    }

    @Test
    void requestsRetryWithoutFailingVersionBeforeMaximumAttempt() {
        assertEquals(KnowledgeProcessingCallbackOutcome.APPLIED_FAILED, apply(failed(true)));
        assertEquals(KnowledgeProcessingStatus.PROCESSING, version.getProcessingStatus());
        verify(events).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
        verifyNoInteractions(audit);
    }

    @Test
    void recordsFinalFailureAtMaximumAttemptWithoutAnotherRetry() {
        for (int number = 2; number <= 3; number++) {
            version.startProcessing(NOW);
            job.beginAttempt(number, NOW);
        }
        current = KnowledgeProcessingAttempt.create(1L, 3, REQUEST_ID, NOW);
        assertEquals(KnowledgeProcessingCallbackOutcome.APPLIED_FAILED, apply(failed(true)));
        assertEquals(KnowledgeProcessingStatus.FAILED, version.getProcessingStatus());
        verify(audit).recordKnowledgeProcessingFailed(eq(version), any(LocalDateTime.class));
        verify(events, never()).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
    }

    @Test
    void rejectedEarlyCallbackDoesNotPoisonProcessingId() {
        var invalid = new KnowledgeProcessingCallback(8001L, 999L, KnowledgeProcessingCallback.Status.COMPLETED,
                24, "HYBRID_POLICY_V1", null, null);
        assertEquals(KnowledgeProcessingCallbackOutcome.CONFLICT, apply(invalid));
        assertNull(job.getProcessingId());
        assertNull(current.getTerminalFingerprint());
        assertEquals(KnowledgeProcessingStatus.PROCESSING, version.getProcessingStatus());
    }

    @Test
    void rejectedChunkProfileDoesNotPoisonProcessingId() {
        var invalid = new KnowledgeProcessingCallback(8001L, 101L, KnowledgeProcessingCallback.Status.COMPLETED,
                24, "INVALID_PROFILE", null, null);
        assertEquals(KnowledgeProcessingCallbackOutcome.CONFLICT, apply(invalid));
        assertNull(job.getProcessingId());
        assertNull(current.getTerminalFingerprint());
    }

    @Test
    void duplicateFailedCallbackDoesNotEnqueueAnotherRetry() {
        apply(failed(true));
        assertEquals(KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE, apply(failed(true)));
        verify(events, times(1)).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
    }

    @Test
    void nonRetryableFailureIsFinalImmediately() {
        assertEquals(KnowledgeProcessingCallbackOutcome.APPLIED_FAILED, apply(failed(false)));
        assertEquals(KnowledgeProcessingStatus.FAILED, version.getProcessingStatus());
        verify(events, never()).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
    }

    @Test
    void unknownRequestWithKnownProcessingIdRemainsConflict() {
        when(attempts.findKnowledgeVersionIdByRequestId(REQUEST_ID)).thenReturn(Optional.empty());
        when(jobs.existsByProcessingId(8001L)).thenReturn(true);
        assertEquals(KnowledgeProcessingCallbackOutcome.CONFLICT, apply(completed()));
        verify(versions, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void whollyUnknownCallbackRemainsStale() {
        when(attempts.findKnowledgeVersionIdByRequestId(REQUEST_ID)).thenReturn(Optional.empty());
        assertEquals(KnowledgeProcessingCallbackOutcome.IGNORED_STALE, apply(completed()));
        verify(versions, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void processingIdAlreadyUsedByAnotherJobIsRejectedBeforeBinding() {
        when(jobs.existsByProcessingId(8001L)).thenReturn(true);
        assertEquals(KnowledgeProcessingCallbackOutcome.CONFLICT, apply(completed()));
        assertNull(job.getProcessingId());
    }

    private KnowledgeProcessingCallbackOutcome apply(KnowledgeProcessingCallback callback) {
        return adapter.applyAtomically(new KnowledgeProcessingCallbackHeaders(REQUEST_ID, 8001L), callback);
    }

    private KnowledgeProcessingCallback completed() {
        return new KnowledgeProcessingCallback(8001L, 101L, KnowledgeProcessingCallback.Status.COMPLETED,
                24, "HYBRID_POLICY_V1", null, null);
    }

    private KnowledgeProcessingCallback failed(boolean retryable) {
        return new KnowledgeProcessingCallback(8001L, 101L, KnowledgeProcessingCallback.Status.FAILED,
                null, null, KnowledgeProcessingCallback.FailureCode.VECTOR_STORE_UNAVAILABLE, retryable);
    }
}
