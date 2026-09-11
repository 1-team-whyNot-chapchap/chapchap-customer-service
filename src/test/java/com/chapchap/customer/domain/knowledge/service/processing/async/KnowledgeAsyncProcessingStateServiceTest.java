package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingAttemptStatus;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingJobStatus;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;
import com.chapchap.customer.domain.knowledge.dto.processing.async.PreparedKnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.global.exception.knowledge.processing.async.CustomerAiKnowledgeJobClientException;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
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
class KnowledgeAsyncProcessingStateServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 16, 0);
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeVersionRepository versionRepository;
    @Mock
    private KnowledgeProcessingJobRepository jobRepository;
    @Mock
    private KnowledgeProcessingAttemptRepository attemptRepository;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private KnowledgeVersion version;
    @Mock
    private KnowledgeDocument document;

    private KnowledgeAsyncProcessingStateService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeAsyncProcessingStateService(
                documentRepository,
                versionRepository,
                jobRepository,
                attemptRepository,
                auditLogWriter,
                eventPublisher
        );
    }

    @Test
    void createsFirstLogicalJobAndAttemptAtomically() {
        stubVersion(KnowledgeProcessingStatus.UPLOADED, 1);
        when(jobRepository.findByKnowledgeVersionIdForUpdate(101L)).thenReturn(Optional.empty());
        when(jobRepository.saveAndFlush(any(KnowledgeProcessingJob.class))).thenAnswer(invocation -> {
            KnowledgeProcessingJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 1L);
            return job;
        });

        PreparedKnowledgeProcessingAttempt prepared = service
                .prepareAttempt(101L, REQUEST_ID, NOW)
                .orElseThrow();

        assertThat(prepared.requestId()).isEqualTo(REQUEST_ID);
        assertThat(prepared.context().attempt()).isEqualTo(1);
        ArgumentCaptor<KnowledgeProcessingAttempt> savedAttempt =
                ArgumentCaptor.forClass(KnowledgeProcessingAttempt.class);
        verify(attemptRepository).save(savedAttempt.capture());
        assertThat(savedAttempt.getValue().getKnowledgeProcessingJobId()).isEqualTo(1L);
        assertThat(savedAttempt.getValue().getRequestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void supersedesPreviousAttemptAndKeepsLogicalJob() {
        stubVersion(KnowledgeProcessingStatus.PROCESSING, 2);
        KnowledgeProcessingJob job = KnowledgeProcessingJob.create(101L, "HYBRID_POLICY_V1", NOW);
        ReflectionTestUtils.setField(job, "id", 1L);
        KnowledgeProcessingAttempt previous = KnowledgeProcessingAttempt.create(
                1L, 1, UUID.randomUUID(), NOW);
        when(jobRepository.findByKnowledgeVersionIdForUpdate(101L)).thenReturn(Optional.of(job));
        when(attemptRepository.findCurrentForUpdate(1L, 1)).thenReturn(Optional.of(previous));

        service.prepareAttempt(101L, REQUEST_ID, NOW.plusSeconds(1)).orElseThrow();

        assertThat(job.getCurrentAttempt()).isEqualTo(2);
        assertThat(previous.getStatus()).isEqualTo(KnowledgeProcessingAttemptStatus.SUPERSEDED);
    }

    @Test
    void doesNotOverwriteTerminalCallbackWithLateSubmissionTimeout() {
        KnowledgeProcessingJob job = KnowledgeProcessingJob.create(101L, "HYBRID_POLICY_V1", NOW);
        ReflectionTestUtils.setField(job, "id", 1L);
        job.bindProcessingId(8001L, NOW);
        KnowledgeProcessingAttempt attempt = KnowledgeProcessingAttempt.create(1L, 1, REQUEST_ID, NOW);
        KnowledgeProcessingCallback completed = new KnowledgeProcessingCallback(
                8001L, 101L, KnowledgeProcessingCallback.Status.COMPLETED,
                10, "HYBRID_POLICY_V1", null, null);
        String fingerprint = KnowledgeProcessingCallbackFingerprint.create(completed);
        attempt.applyTerminal(completed, fingerprint, NOW);
        job.applyCompleted(fingerprint, 10, NOW);
        when(attemptRepository.findByRequestId(REQUEST_ID)).thenReturn(Optional.of(attempt));
        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        service.markSubmissionFailed(
                REQUEST_ID,
                CustomerAiKnowledgeJobClientException.Reason.TIMEOUT,
                true,
                NOW.plusSeconds(1)
        );

        assertThat(job.getStatus()).isEqualTo(KnowledgeProcessingJobStatus.COMPLETED);
        verify(versionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void skipsDelayedRetryAfterVersionAlreadyBecameReady() {
        when(versionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(version));
        when(version.getProcessingStatus()).thenReturn(KnowledgeProcessingStatus.READY);

        assertThat(service.prepareAttempt(101L, REQUEST_ID, NOW)).isEmpty();
        verify(documentRepository, never()).findById(any());
    }

    private void stubVersion(KnowledgeProcessingStatus status, int attemptCount) {
        when(versionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(version));
        when(version.getProcessingStatus()).thenReturn(status);
        when(version.getKnowledgeDocumentId()).thenReturn(10L);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(version.getProcessingAttemptCount()).thenReturn(attemptCount);
        when(version.getId()).thenReturn(101L);
        when(version.getChunkProfile()).thenReturn("HYBRID_POLICY_V1");
        when(version.getObjectKey()).thenReturn("knowledge/source.pdf");
        when(version.getContentType()).thenReturn("application/pdf");
        when(version.getFileSize()).thenReturn(1024L);
        when(version.getVersion()).thenReturn("v1");
        when(version.getEffectiveFrom()).thenReturn(NOW);
        when(document.getDocumentKey()).thenReturn("refund-policy");
        when(document.getSourceService()).thenReturn("SUBSCRIPTION");
        when(document.getCategory()).thenReturn("POLICY");
    }
}
