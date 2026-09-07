package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.entity.ConsultationSenderType;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.global.error.custom.consultation.ConsultationStateException;
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
                ConsultationMessage.create(consultation, ConsultationSenderType.ADMIN, 11L, "답변", 2, NOW)));
        when(jobRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ConsultationSummaryJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 7001L);
            return job;
        });

        PreparedConsultationSummaryJob prepared = service.prepare(501L, NOW).orElseThrow();

        assertThat(prepared.messages()).extracting("content").containsExactly("첫 질문", "답변");
        assertThat(prepared.messages()).extracting("senderType")
                .containsExactly(
                        CustomerAiConsultationSummaryCommand.SenderType.USER,
                        CustomerAiConsultationSummaryCommand.SenderType.ADMIN);
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

        assertThat(service.prepare(501L, NOW)).isEmpty();
        verify(messageRepository, never()).findByConsultation_IdOrderBySequenceNoAsc(any());
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSummaryPreparationBeforeConsultationIsClosed() {
        Consultation consultation = Consultation.create(77L, NOW);
        ReflectionTestUtils.setField(consultation, "id", 501L);
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> service.prepare(501L, NOW))
                .isInstanceOf(ConsultationStateException.class);

        verify(jobRepository, never()).saveAndFlush(any());
    }

    private Consultation closedConsultation() {
        Consultation consultation = Consultation.create(77L, NOW);
        ReflectionTestUtils.setField(consultation, "id", 501L);
        consultation.close(NOW);
        return consultation;
    }
}
