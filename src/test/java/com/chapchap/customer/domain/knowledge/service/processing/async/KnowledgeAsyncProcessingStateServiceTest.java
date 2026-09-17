package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingAttemptStatus;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingJobStatus;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingRetryRequestedEvent;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;
import com.chapchap.customer.domain.knowledge.response.processing.async.CustomerAiKnowledgeJobAccepted;
import com.chapchap.customer.global.exception.knowledge.processing.async.CustomerAiKnowledgeJobClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Repository doubles exercise state transitions. This is NOT a MySQL deadlock test.
class KnowledgeAsyncProcessingStateServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 12, 0);
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private KnowledgeDocumentRepository docs;
    private KnowledgeVersionRepository versions;
    private KnowledgeProcessingJobRepository jobs;
    private KnowledgeProcessingAttemptRepository attempts;
    private AuditLogWriter audit;
    private ApplicationEventPublisher events;
    private KnowledgeAsyncProcessingStateService service;
    private KnowledgeVersion version;
    private KnowledgeProcessingJob job;
    private final Map<Integer, KnowledgeProcessingAttempt> saved = new HashMap<>();

    @BeforeEach
    void setUp() {
        docs = mock(KnowledgeDocumentRepository.class);
        versions = mock(KnowledgeVersionRepository.class);
        jobs = mock(KnowledgeProcessingJobRepository.class);
        attempts = mock(KnowledgeProcessingAttemptRepository.class);
        audit = mock(AuditLogWriter.class);
        events = mock(ApplicationEventPublisher.class);
        service = new KnowledgeAsyncProcessingStateService(docs, versions, jobs, attempts, audit, events);
        version = KnowledgeVersion.uploaded(10L, "v1", "knowledge/source", "source.md", "text/markdown",
                100L, NOW, 1L, NOW);
        ReflectionTestUtils.setField(version, "id", 101L);
        when(versions.findByIdForUpdate(101L)).thenReturn(Optional.of(version));
        when(docs.findById(10L)).thenReturn(Optional.of(KnowledgeDocument.create(
                "policy", "SUBSCRIPTION", "POLICY", "정책", NOW)));
        when(jobs.findByKnowledgeVersionId(101L)).thenAnswer(i -> Optional.ofNullable(job));
        when(jobs.saveAndFlush(any(KnowledgeProcessingJob.class))).thenAnswer(i -> {
            job = i.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 1L);
            return job;
        });
        when(attempts.save(any(KnowledgeProcessingAttempt.class))).thenAnswer(i -> {
            KnowledgeProcessingAttempt attempt = i.getArgument(0);
            saved.put(attempt.getAttempt(), attempt);
            return attempt;
        });
        when(attempts.findCurrentForUpdate(eq(1L), anyInt()))
                .thenAnswer(i -> Optional.ofNullable(saved.get(i.<Integer>getArgument(1))));
        when(attempts.findKnowledgeVersionIdByRequestId(any(UUID.class))).thenAnswer(i ->
                saved.values().stream().anyMatch(a -> a.getRequestId().equals(i.getArgument(0)))
                        ? Optional.of(101L) : Optional.empty());
    }

    @Test
    void createsFirstLogicalJobAndAttemptAtomically() {
        var prepared = service.prepareAttempt(101L, REQUEST_ID, NOW, 0).orElseThrow();
        assertEquals(REQUEST_ID, prepared.requestId());
        assertEquals(1, prepared.context().attempt());
        assertEquals(1L, saved.get(1).getKnowledgeProcessingJobId());
        assertEquals(REQUEST_ID, saved.get(1).getRequestId());
        var order = inOrder(versions, jobs);
        order.verify(versions).findByIdForUpdate(101L);
        order.verify(jobs).findByKnowledgeVersionId(101L);
        verify(jobs, never()).findByKnowledgeVersionIdForUpdate(anyLong());
        verify(docs, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void supersedesPreviousAttemptAndKeepsLogicalJob() {
        start();
        service.markSubmissionFailed(REQUEST_ID, CustomerAiKnowledgeJobClientException.Reason.TIMEOUT, true, NOW);
        KnowledgeProcessingJob original = job;
        var prepared = service.prepareAttempt(101L, UUID.randomUUID(), NOW.plusSeconds(5), 1).orElseThrow();
        assertSame(original, job);
        assertEquals(2, prepared.context().attempt());
        assertEquals(2, job.getCurrentAttempt());
        assertEquals(KnowledgeProcessingAttemptStatus.SUPERSEDED, saved.get(1).getStatus());
    }

    @Test
    void doesNotOverwriteTerminalCallbackWithLateSubmissionTimeout() {
        start();
        complete();
        service.markSubmissionFailed(REQUEST_ID, CustomerAiKnowledgeJobClientException.Reason.TIMEOUT,
                true, NOW.plusSeconds(1));
        assertEquals(KnowledgeProcessingJobStatus.COMPLETED, job.getStatus());
        assertEquals(KnowledgeProcessingStatus.READY, version.getProcessingStatus());
        verify(events, never()).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
        verify(audit, never()).recordKnowledgeProcessingFailed(any(), any());
    }

    @Test
    void skipsDelayedRetryAfterVersionAlreadyBecameReady() {
        version.startProcessing(NOW);
        version.completeProcessing(NOW);
        assertTrue(service.prepareAttempt(101L, REQUEST_ID, NOW, 1).isEmpty());
        verify(jobs, never()).findByKnowledgeVersionId(anyLong());
        verify(docs, never()).findById(anyLong());
    }

    @Test
    void duplicateInitialSubmissionDoesNotConsumeAnotherAttempt() {
        start();
        assertTrue(service.prepareAttempt(101L, UUID.randomUUID(), NOW, 0).isEmpty());
        assertEquals(1, version.getProcessingAttemptCount());
        assertEquals(1, saved.size());
    }

    @Test
    void delayedRetryEventCannotConsumeALaterFailedAttempt() {
        start();
        service.markSubmissionFailed(REQUEST_ID, CustomerAiKnowledgeJobClientException.Reason.TIMEOUT, true, NOW);
        UUID second = UUID.randomUUID();
        service.prepareAttempt(101L, second, NOW.plusSeconds(5), 1).orElseThrow();
        service.markSubmissionFailed(second, CustomerAiKnowledgeJobClientException.Reason.TIMEOUT, true, NOW.plusSeconds(6));
        assertTrue(service.prepareAttempt(101L, UUID.randomUUID(), NOW.plusSeconds(7), 1).isEmpty());
        assertEquals(2, version.getProcessingAttemptCount());
    }

    @Test
    void duplicateSubmissionFailureSchedulesOnlyOneRetry() {
        start();
        service.markSubmissionFailed(REQUEST_ID, CustomerAiKnowledgeJobClientException.Reason.TIMEOUT, true, NOW);
        service.markSubmissionFailed(REQUEST_ID, CustomerAiKnowledgeJobClientException.Reason.TIMEOUT, true, NOW);
        verify(events, times(1)).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
    }

    @Test
    void lateAcceptedResponseCannotRestoreTerminalAttempt() {
        start();
        complete();
        String fingerprint = saved.get(1).getTerminalFingerprint();
        service.markAccepted(REQUEST_ID, new CustomerAiKnowledgeJobAccepted(8001L, 101L), NOW.plusSeconds(1));
        assertEquals(KnowledgeProcessingAttemptStatus.TERMINAL, saved.get(1).getStatus());
        assertEquals(fingerprint, saved.get(1).getTerminalFingerprint());
        assertEquals(KnowledgeProcessingJobStatus.COMPLETED, job.getStatus());
    }

    @Test
    void oldAcceptedResponseCannotModifyNewAttempt() {
        start();
        service.markSubmissionFailed(REQUEST_ID, CustomerAiKnowledgeJobClientException.Reason.TIMEOUT, true, NOW);
        service.prepareAttempt(101L, UUID.randomUUID(), NOW.plusSeconds(5), 1).orElseThrow();
        service.markAccepted(REQUEST_ID, new CustomerAiKnowledgeJobAccepted(8001L, 101L), NOW.plusSeconds(6));
        assertEquals(2, job.getCurrentAttempt());
        assertEquals(KnowledgeProcessingJobStatus.PENDING, job.getStatus());
        assertNull(job.getProcessingId());
    }

    @Test
    void thirdFailureEndsVersionAndDoesNotScheduleFourthAttempt() {
        start();
        UUID current = REQUEST_ID;
        for (int number = 1; number <= 3; number++) {
            service.markSubmissionFailed(current, CustomerAiKnowledgeJobClientException.Reason.TIMEOUT, true, NOW);
            if (number < 3) {
                current = UUID.randomUUID();
                service.prepareAttempt(101L, current, NOW, number).orElseThrow();
            }
        }
        assertEquals(KnowledgeProcessingStatus.FAILED, version.getProcessingStatus());
        assertEquals(3, saved.size());
        verify(events, times(2)).publishEvent(any(KnowledgeProcessingRetryRequestedEvent.class));
        verify(audit).recordKnowledgeProcessingFailed(version, NOW);
    }

    private void start() {
        service.prepareAttempt(101L, REQUEST_ID, NOW, 0).orElseThrow();
    }

    private void complete() {
        var callback = new KnowledgeProcessingCallback(8001L, 101L, KnowledgeProcessingCallback.Status.COMPLETED,
                10, "HYBRID_POLICY_V1", null, null);
        String fingerprint = KnowledgeProcessingCallbackFingerprint.create(callback);
        saved.get(1).applyTerminal(callback, fingerprint, NOW);
        job.bindProcessingId(8001L, NOW);
        job.applyCompleted(fingerprint, 10, NOW);
        version.completeProcessing(NOW);
    }
}
