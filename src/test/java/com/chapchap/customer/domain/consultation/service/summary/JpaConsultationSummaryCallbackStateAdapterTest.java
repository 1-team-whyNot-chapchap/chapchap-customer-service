package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryCallbackOutcome;
import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryJobStatus;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallbackHeaders;
import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummary;
import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummaryJob;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryJobRepository;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryRepository;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaConsultationSummaryCallbackStateAdapterTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private ConsultationSummaryJobRepository jobRepository;
    @Mock
    private ConsultationSummaryRepository summaryRepository;
    @Mock
    private ConsultationRepository consultationRepository;
    @Mock
    private AuditLogWriter auditLogWriter;

    private JpaConsultationSummaryCallbackStateAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaConsultationSummaryCallbackStateAdapter(
                jobRepository, summaryRepository, consultationRepository, auditLogWriter);
    }

    @Test
    void completedCallbackPersistsAiSummaryAndKeepsConsultationClosed() {
        ConsultationSummaryJob job = job();
        Consultation consultation = closedConsultation();
        stubCurrent(job, consultation);
        when(summaryRepository.findByConsultationIdForUpdate(501L)).thenReturn(Optional.empty());

        ConsultationSummaryCallbackOutcome outcome = adapter.applyAtomically(
                headers(), completed("배송 지연 확인 및 환불 안내"));

        assertThat(outcome).isEqualTo(ConsultationSummaryCallbackOutcome.APPLIED_COMPLETED);
        assertThat(job.getStatus()).isEqualTo(ConsultationSummaryJobStatus.COMPLETED);
        assertThat(consultation.getStatus().name()).isEqualTo("CLOSED");
        ArgumentCaptor<ConsultationSummary> summary = ArgumentCaptor.forClass(ConsultationSummary.class);
        verify(summaryRepository).save(summary.capture());
        assertThat(summary.getValue().getAiSummary()).isEqualTo("배송 지연 확인 및 환불 안내");
        verify(auditLogWriter).recordConsultationSummaryResult(
                eq(501L), eq(7001L), eq(true), eq(null), any(LocalDateTime.class));
    }

    @Test
    void failedCallbackDoesNotReopenClosedConsultation() {
        ConsultationSummaryJob job = job();
        Consultation consultation = closedConsultation();
        stubCurrent(job, consultation);

        ConsultationSummaryCallbackOutcome outcome = adapter.applyAtomically(
                headers(), failed());

        assertThat(outcome).isEqualTo(ConsultationSummaryCallbackOutcome.APPLIED_FAILED);
        assertThat(job.getStatus()).isEqualTo(ConsultationSummaryJobStatus.FAILED);
        assertThat(consultation.getStatus().name()).isEqualTo("CLOSED");
        verify(summaryRepository, never()).save(any());
        verify(auditLogWriter).recordConsultationSummaryResult(
                eq(501L), eq(7001L), eq(false), eq("LLM_UNAVAILABLE"), any(LocalDateTime.class));
    }

    @Test
    void exactTerminalReplayIsDuplicateAndDifferentReplayConflicts() {
        ConsultationSummaryJob job = job();
        ConsultationSummaryCallback first = completed("첫 요약");
        job.applyCompleted(ConsultationSummaryCallbackFingerprint.create(first), LocalDateTime.now());
        when(jobRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(job));

        assertThat(adapter.applyAtomically(headers(), first))
                .isEqualTo(ConsultationSummaryCallbackOutcome.IGNORED_DUPLICATE);
        assertThat(adapter.applyAtomically(headers(), completed("다른 요약")))
                .isEqualTo(ConsultationSummaryCallbackOutcome.CONFLICT);
        verify(consultationRepository, never()).findByIdForMessageWrite(any());
    }

    @Test
    void rejectsCallbackWhileConsultationIsNotClosed() {
        ConsultationSummaryJob job = job();
        Consultation open = Consultation.create(77L, LocalDateTime.now());
        ReflectionTestUtils.setField(open, "id", 501L);
        stubCurrent(job, open);

        assertThat(adapter.applyAtomically(headers(), completed("요약")))
                .isEqualTo(ConsultationSummaryCallbackOutcome.CONFLICT);
        verify(summaryRepository, never()).save(any());
    }

    private void stubCurrent(ConsultationSummaryJob job, Consultation consultation) {
        when(jobRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(job));
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));
    }

    private ConsultationSummaryJob job() {
        ConsultationSummaryJob job = ConsultationSummaryJob.create(501L, REQUEST_ID, LocalDateTime.now());
        ReflectionTestUtils.setField(job, "id", 7001L);
        return job;
    }

    private Consultation closedConsultation() {
        Consultation consultation = Consultation.create(77L, LocalDateTime.now());
        ReflectionTestUtils.setField(consultation, "id", 501L);
        consultation.close(LocalDateTime.now());
        return consultation;
    }

    private ConsultationSummaryCallbackHeaders headers() {
        return new ConsultationSummaryCallbackHeaders(REQUEST_ID, 7001L);
    }

    private ConsultationSummaryCallback completed(String summary) {
        return new ConsultationSummaryCallback(
                7001L, 501L, ConsultationSummaryCallback.Status.COMPLETED,
                summary, null, null);
    }

    private ConsultationSummaryCallback failed() {
        return new ConsultationSummaryCallback(
                7001L, 501L, ConsultationSummaryCallback.Status.FAILED,
                null, ConsultationSummaryCallback.FailureCode.LLM_UNAVAILABLE, true);
    }
}
