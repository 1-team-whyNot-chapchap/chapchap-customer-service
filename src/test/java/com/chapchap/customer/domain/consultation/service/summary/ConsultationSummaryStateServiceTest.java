package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.dto.summary.CustomerAiConsultationSummaryCommand;
import com.chapchap.customer.domain.consultation.dto.summary.PreparedConsultationSummaryJob;
import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummaryJob;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryJobRepository;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.constant.ConsultationSenderType;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.global.exception.consultation.ConsultationStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationSummaryStateServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 16, 0);

    @Mock
    private ConsultationRepository consultationRepository;
    @Mock
    private ConsultationMessageRepository messageRepository;
    @Mock
    private ConsultationSummaryJobRepository jobRepository;

    private ConsultationSummaryStateService service;

    @BeforeEach
    void setUp() {
        service = new ConsultationSummaryStateService(
                consultationRepository, messageRepository, jobRepository);
    }

    @Test
    void createsOneJobFromClosedConsultationMessagesInSequenceOrder() {
        Consultation consultation = closedConsultation();
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));
        when(jobRepository.findByConsultationId(501L)).thenReturn(Optional.empty());
        when(messageRepository.findByConsultation_IdOrderBySequenceNoAsc(501L)).thenReturn(List.of(
                ConsultationMessage.create(consultation, ConsultationSenderType.USER, 77L, "첫 질문", 1, NOW),
                ConsultationMessage.create(consultation, ConsultationSenderType.AI, null, "답변", 2, NOW)));
        when(jobRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ConsultationSummaryJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 7001L);
            return job;
        });

        PreparedConsultationSummaryJob prepared = service.prepare(501L, 2, NOW).orElseThrow();

        assertThat(prepared.messages()).extracting("content").containsExactly("첫 질문", "답변");
        assertThat(prepared.messages()).extracting("senderType")
                .containsExactly(
                        CustomerAiConsultationSummaryCommand.SenderType.USER,
                        CustomerAiConsultationSummaryCommand.SenderType.AI);
        ArgumentCaptor<ConsultationSummaryJob> job = ArgumentCaptor.forClass(ConsultationSummaryJob.class);
        verify(jobRepository).saveAndFlush(job.capture());
        assertThat(job.getValue().getConsultationId()).isEqualTo(501L);
    }

    @Test
    void skipsCreationWhenConsultationAlreadyHasSummaryJob() {
        Consultation consultation = closedConsultation();
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));
        when(jobRepository.findByConsultationId(501L)).thenReturn(Optional.of(
                ConsultationSummaryJob.create(501L, java.util.UUID.randomUUID(), NOW)));

        assertThat(service.prepare(501L, 2, NOW)).isEmpty();
        verify(messageRepository, never()).findByConsultation_IdOrderBySequenceNoAsc(any());
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSummaryPreparationBeforeConsultationIsClosed() {
        Consultation consultation = Consultation.create(77L, NOW);
        ReflectionTestUtils.setField(consultation, "id", 501L);
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> service.prepare(501L, 2, NOW))
                .isInstanceOf(ConsultationStateException.class);

        verify(jobRepository, never()).saveAndFlush(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.chapchap.customer.domain.consultation.constant.ConsultationStatus.class,
            names = {"WAITING_ADMIN", "IN_PROGRESS", "CLOSED"})
    void handoffSnapshotExcludesMessagesAfterCutoffEvenWhenAgentHasAcceptedOrClosed(
            com.chapchap.customer.domain.consultation.constant.ConsultationStatus status) {
        Consultation consultation = closedConsultation();
        ReflectionTestUtils.setField(consultation, "status", status);
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));
        when(messageRepository.findByConsultation_IdOrderBySequenceNoAsc(501L)).thenReturn(List.of(
                ConsultationMessage.create(consultation, ConsultationSenderType.USER, 77L, "고객 질문", 1, NOW),
                ConsultationMessage.create(consultation, ConsultationSenderType.AI, null, "AI 응답", 2, NOW),
                ConsultationMessage.create(consultation, ConsultationSenderType.ADMIN, 11L, "상담사 답변", 3, NOW),
                ConsultationMessage.create(consultation, ConsultationSenderType.USER, 77L, "연결 후 질문", 4, NOW)));
        when(jobRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ConsultationSummaryJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 7001L);
            return job;
        });
        assertThat(service.prepare(501L, 2, NOW).orElseThrow().messages())
                .extracting("content").containsExactly("고객 질문", "AI 응답");
    }

    private Consultation closedConsultation() {
        Consultation consultation = Consultation.create(77L, NOW);
        ReflectionTestUtils.setField(consultation, "id", 501L);
        consultation.close(NOW);
        return consultation;
    }
}
