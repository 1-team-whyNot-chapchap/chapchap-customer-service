package com.chapchap.customer.domain.consultation.service;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;
import com.chapchap.customer.domain.consultation.event.ConsultationMessageSavedEvent;
import com.chapchap.customer.domain.consultation.request.ConsultationRealtimeMessageRequest;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.domain.consultation.request.ConsultationCreateRequest;
import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceTest {
    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private ConsultationMessageRepository consultationMessageRepository;

    @Mock
    private AuditLogWriter auditLogWriter;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private ConsultationService consultationService;

    @Test
    void createsAiHandlingConsultationAndFirstUserMessageTogether() {
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(invocation -> {
            Consultation consultation = invocation.getArgument(0);
            ReflectionTestUtils.setField(consultation, "id", 1L);
            return consultation;
        });
        when(consultationMessageRepository.save(any(ConsultationMessage.class))).thenAnswer(invocation -> {
            ConsultationMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 10L);
            return message;
        });
        ArgumentCaptor<ConsultationMessage> messageCaptor = ArgumentCaptor.forClass(ConsultationMessage.class);

        var response = consultationService.createConsultation(7L, new ConsultationCreateRequest("  배송 문의  "));

        verify(consultationMessageRepository).save(messageCaptor.capture());
        ConsultationMessage firstMessage = messageCaptor.getValue();
        assertThat(response.consultationId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ConsultationStatus.AI_HANDLING);
        assertThat(response.initialMessage().messageId()).isEqualTo(10L);
        assertThat(firstMessage.getSenderType()).isEqualTo(com.chapchap.customer.domain.consultation.entity.ConsultationSenderType.USER);
        assertThat(firstMessage.getSenderUserId()).isEqualTo(7L);
        assertThat(firstMessage.getSequenceNo()).isEqualTo(1);
        assertThat(firstMessage.getContent()).isEqualTo("배송 문의");
    }

    @Test
    void returnsOnlyConsultationOwnedByCurrentUser() {
        Consultation consultation = consultation(3L, 7L);
        when(consultationRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(consultation));

        var response = consultationService.findMyConsultation(7L, 3L);

        assertThat(response.consultationId()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(ConsultationStatus.AI_HANDLING);
    }

    @Test
    void hidesConsultationNotOwnedByCurrentUser() {
        when(consultationRepository.findByIdAndUserId(3L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.findMyConsultation(8L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCustomResponseCode())
                                .isEqualTo(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR));
    }

    @Test
    void returnsPersistedMessagesInRepositorySequenceOrder() {
        Consultation consultation = consultation(3L, 7L);
        ConsultationMessage firstMessage = ConsultationMessage.firstUserMessage(consultation, 7L, "첫 문의", LocalDateTime.now());
        ReflectionTestUtils.setField(firstMessage, "id", 10L);
        when(consultationRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(consultation));
        when(consultationMessageRepository.findByConsultation_IdOrderBySequenceNoAsc(3L))
                .thenReturn(List.of(firstMessage));

        var response = consultationService.findMyConsultationMessages(7L, 3L);

        verify(consultationMessageRepository).findByConsultation_IdOrderBySequenceNoAsc(eq(3L));
        assertThat(response.consultationId()).isEqualTo(3L);
        assertThat(response.messages()).extracting("sequenceNo").containsExactly(1);
    }

    @Test
    void transfersOnlyOwnedAiHandlingConsultationToWaitingAdminAndRecordsAudit() {
        Consultation consultation = consultation(3L, 7L);
        when(consultationRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(consultation));

        var response = consultationService.requestAdminHandoff(7L, 3L);

        assertThat(response.status()).isEqualTo(ConsultationStatus.WAITING_ADMIN);
        assertThat(response.escalatedAt()).isNotNull();
        verify(auditLogWriter).recordConsultationEscalated(eq(7L), eq(consultation), eq("AI_HANDLING"), any());
    }

    @Test
    void acceptsWaitingConsultationWithConditionalUpdateAndRecordsAdminAudit() {
        Consultation consultation = consultation(3L, 7L);
        ReflectionTestUtils.setField(consultation, "status", ConsultationStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(consultation, "assignedAdminId", 11L);
        when(consultationRepository.acceptWaitingConsultation(eq(3L), eq(11L), any())).thenReturn(1);
        when(consultationRepository.findById(3L)).thenReturn(Optional.of(consultation));

        var response = consultationService.acceptConsultation(11L, 3L);

        assertThat(response.status()).isEqualTo(ConsultationStatus.IN_PROGRESS);
        assertThat(response.assignedAdminId()).isEqualTo(11L);
        verify(auditLogWriter).recordConsultationAccepted(eq(11L), eq(consultation), eq("WAITING_ADMIN"), any());
    }

    @Test
    void distinguishesMissingConsultationFromAcceptanceContention() {
        when(consultationRepository.acceptWaitingConsultation(eq(3L), eq(11L), any())).thenReturn(0);
        when(consultationRepository.existsById(3L)).thenReturn(false);

        assertThatThrownBy(() -> consultationService.acceptConsultation(11L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCustomResponseCode())
                                .isEqualTo(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR));

        when(consultationRepository.existsById(3L)).thenReturn(true);
        assertThatThrownBy(() -> consultationService.acceptConsultation(11L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCustomResponseCode())
                                .isEqualTo(CustomResponseCode.INVALID_STATE_ERROR));
    }

    @Test
    void savesParticipantMessageWithNextSequenceAndSchedulesPostCommitPublication() {
        Consultation consultation = consultation(3L, 7L);
        ReflectionTestUtils.setField(consultation, "status", ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findByIdForMessageWrite(3L)).thenReturn(Optional.of(consultation));
        when(consultationMessageRepository.findTopByConsultation_IdOrderBySequenceNoDesc(3L)).thenReturn(Optional.empty());
        when(consultationMessageRepository.save(any(ConsultationMessage.class))).thenAnswer(invocation -> {
            ConsultationMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 10L);
            return message;
        });
        ArgumentCaptor<ConsultationMessage> messageCaptor = ArgumentCaptor.forClass(ConsultationMessage.class);
        ArgumentCaptor<ConsultationMessageSavedEvent> eventCaptor = ArgumentCaptor.forClass(ConsultationMessageSavedEvent.class);

        var response = consultationService.saveRealtimeMessage(
                new GatewayUserPrincipal("7", RolePolicy.CUSTOMER),
                3L,
                new ConsultationRealtimeMessageRequest("  상담 메시지  ")
        );

        verify(consultationMessageRepository).save(messageCaptor.capture());
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(messageCaptor.getValue().getSequenceNo()).isEqualTo(1);
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("상담 메시지");
        assertThat(response.messageId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().message()).isEqualTo(response);
    }

    @Test
    void rejectsRealtimeMessageFromNonParticipant() {
        Consultation consultation = consultation(3L, 7L);
        ReflectionTestUtils.setField(consultation, "status", ConsultationStatus.IN_PROGRESS);
        when(consultationRepository.findByIdForMessageWrite(3L)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> consultationService.saveRealtimeMessage(
                new GatewayUserPrincipal("8", RolePolicy.CUSTOMER),
                3L,
                new ConsultationRealtimeMessageRequest("상담 메시지")
        )).isInstanceOf(AccessDeniedException.class);
    }

    private Consultation consultation(Long consultationId, Long userId) {
        Consultation consultation = Consultation.create(userId, LocalDateTime.now());
        ReflectionTestUtils.setField(consultation, "id", consultationId);
        return consultation;
    }
}
