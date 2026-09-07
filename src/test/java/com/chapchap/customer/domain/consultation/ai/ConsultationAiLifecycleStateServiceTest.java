package com.chapchap.customer.domain.consultation.ai;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessageSource;
import com.chapchap.customer.domain.consultation.entity.ConsultationSenderType;
import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;
import com.chapchap.customer.domain.consultation.event.ConsultationAiResponseRequestedEvent;
import com.chapchap.customer.domain.consultation.event.ConsultationMessageSavedEvent;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageSourceRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.global.security.constant.RolePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationAiLifecycleStateServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 17, 0);
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private ConsultationRepository consultationRepository;
    @Mock
    private ConsultationMessageRepository messageRepository;
    @Mock
    private ConsultationMessageSourceRepository sourceRepository;
    @Mock
    private KnowledgeVersionRepository knowledgeVersionRepository;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ConsultationAiLifecycleStateService service;

    @BeforeEach
    void setUp() {
        service = new ConsultationAiLifecycleStateService(
                consultationRepository,
                messageRepository,
                sourceRepository,
                knowledgeVersionRepository,
                auditLogWriter,
                eventPublisher
        );
    }

    @Test
    void preparesPolicyOnlyRequestWithPreviousConversationContext() {
        Consultation consultation = consultation();
        ConsultationMessage previous = message(consultation, 9001L, ConsultationSenderType.AI, null, "이전 답변", 2);
        ConsultationMessage trigger = message(consultation, 9002L, ConsultationSenderType.USER, 42L, "환불 정책", 3);
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));
        when(messageRepository.findByTriggerMessageId(9002L)).thenReturn(Optional.empty());
        when(messageRepository.findById(9002L)).thenReturn(Optional.of(trigger));
        when(messageRepository.findByConsultation_IdOrderBySequenceNoAsc(501L))
                .thenReturn(List.of(previous, trigger));
        when(knowledgeVersionRepository.findApprovedVersionIds(any(), any())).thenReturn(List.of(101L, 102L));

        CustomerAiConsultationCommand command = service.prepare(event(), REQUEST_ID)
                .orElseThrow()
                .command();

        assertThat(command.triggerMessageId()).isEqualTo(9002L);
        assertThat(command.knowledgeVersionIds()).containsExactly(101L, 102L);
        assertThat(CustomerAiConsultationRequest.from(command).knowledgeVersionIds()).containsExactly(101L, 102L);
        assertThat(command.message()).isEqualTo("환불 정책");
        assertThat(command.conversationContext()).containsExactly("이전 답변");
        assertThat(command.subject().allowedAiScopes()).containsExactly("customer-ai.policy.read");
        assertThat(command.subject().role()).isEqualTo(RolePolicy.CUSTOMER);
    }

    @Test
    void appliesAnswerAndPersistsOnlyValidatedEvidence() {
        Consultation consultation = consultation();
        ConsultationMessage last = message(consultation, 9002L, ConsultationSenderType.USER, 42L, "환불 정책", 1);
        KnowledgeVersion version = readyVersion(101L);
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));
        when(messageRepository.findByTriggerMessageId(9002L)).thenReturn(Optional.empty());
        when(messageRepository.findTopByConsultation_IdOrderBySequenceNoDesc(501L)).thenReturn(Optional.of(last));
        when(messageRepository.save(any(ConsultationMessage.class))).thenAnswer(invocation -> {
            ConsultationMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 9003L);
            return message;
        });
        when(knowledgeVersionRepository.findAllById(List.of(101L))).thenReturn(List.of(version));
        CustomerAiConsultationResult result = new CustomerAiConsultationResult(
                REQUEST_ID,
                CustomerAiConsultationResult.Decision.ANSWER,
                "환불은 결제 후 7일 이내 가능합니다.",
                CustomerAiConsultationResult.Route.POLICY,
                false,
                false,
                List.of(new CustomerAiConsultationResult.Evidence(101L, "chunk-1", 1, 0.91))
        );

        assertThat(service.apply(command(), result, NOW)).isTrue();

        ArgumentCaptor<ConsultationMessage> aiMessage = ArgumentCaptor.forClass(ConsultationMessage.class);
        verify(messageRepository).save(aiMessage.capture());
        assertThat(aiMessage.getValue().getSenderType()).isEqualTo(ConsultationSenderType.AI);
        assertThat(aiMessage.getValue().getSenderUserId()).isNull();
        assertThat(aiMessage.getValue().getTriggerMessageId()).isEqualTo(9002L);
        assertThat(aiMessage.getValue().getSequenceNo()).isEqualTo(2);
        verify(sourceRepository).saveAll(any());
        verify(eventPublisher).publishEvent(any(ConsultationMessageSavedEvent.class));
        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.AI_HANDLING);
    }

    @Test
    void appliesHandoffWithoutCreatingAiMessage() {
        Consultation consultation = consultation();
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));
        when(messageRepository.findByTriggerMessageId(9002L)).thenReturn(Optional.empty());
        CustomerAiConsultationResult result = new CustomerAiConsultationResult(
                REQUEST_ID,
                CustomerAiConsultationResult.Decision.HANDOFF,
                null,
                CustomerAiConsultationResult.Route.UNSUPPORTED,
                false,
                true,
                List.of()
        );

        assertThat(service.apply(command(), result, NOW)).isTrue();

        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.WAITING_ADMIN);
        verify(messageRepository, never()).save(any());
        verify(auditLogWriter).recordConsultationAiEscalated(
                eq(consultation), eq("AI_HANDLING"), eq("HANDOFF"), eq(NOW));
    }

    @Test
    void ignoresLateResultAfterUserAlreadyRequestedAdminHandoff() {
        Consultation consultation = consultation();
        consultation.requestAdminHandoff(NOW.minusSeconds(1));
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));

        CustomerAiConsultationResult result = new CustomerAiConsultationResult(
                REQUEST_ID,
                CustomerAiConsultationResult.Decision.ANSWER,
                "늦은 답변",
                CustomerAiConsultationResult.Route.UNSUPPORTED,
                false,
                false,
                List.of()
        );

        assertThat(service.apply(command(), result, NOW)).isFalse();
        verify(messageRepository, never()).save(any());
    }

    @Test
    void handsOffAfterTransportFailureWithoutSavingAnswer() {
        Consultation consultation = consultation();
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation));
        when(messageRepository.findByTriggerMessageId(9002L)).thenReturn(Optional.empty());

        assertThat(service.handoffAfterFailure(command(), "TIMEOUT", NOW)).isTrue();

        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.WAITING_ADMIN);
        verify(auditLogWriter).recordConsultationAiEscalated(
                eq(consultation), eq("AI_HANDLING"), eq("TIMEOUT"), eq(NOW));
    }

    @Test
    void rejectsCurrentStateRouteUntilCapabilityRuntimeIsConnected() {
        CustomerAiConsultationResult result = new CustomerAiConsultationResult(
                REQUEST_ID,
                CustomerAiConsultationResult.Decision.ANSWER,
                "상태 답변",
                CustomerAiConsultationResult.Route.USER_STATE,
                false,
                false,
                List.of()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.apply(command(), result, NOW))
                .isInstanceOf(com.chapchap.customer.global.error.custom.consultation.ConsultationStateException.class);

        verify(consultationRepository, never()).findByIdForMessageWrite(any());
    }

    private ConsultationAiResponseRequestedEvent event() {
        return new ConsultationAiResponseRequestedEvent(501L, 9002L, 42L, RolePolicy.CUSTOMER);
    }

    private CustomerAiConsultationCommand command() {
        return serviceCommand(REQUEST_ID);
    }

    private CustomerAiConsultationCommand serviceCommand(UUID requestId) {
        return new CustomerAiConsultationCommand(
                requestId,
                501L,
                9002L,
                new com.chapchap.customer.global.security.customerai.CustomerAiSubjectAssertionRequest(
                        42L,
                        RolePolicy.CUSTOMER,
                        List.of("customer-ai.policy.read"),
                        requestId,
                        501L),
                "환불 정책",
                List.of(),
                List.of(101L)
        );
    }

    private Consultation consultation() {
        Consultation consultation = Consultation.create(42L, NOW.minusMinutes(1));
        ReflectionTestUtils.setField(consultation, "id", 501L);
        return consultation;
    }

    private ConsultationMessage message(
            Consultation consultation,
            Long id,
            ConsultationSenderType senderType,
            Long senderUserId,
            String content,
            int sequence
    ) {
        ConsultationMessage message = ConsultationMessage.create(
                consultation, senderType, senderUserId, content, sequence, NOW);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private KnowledgeVersion readyVersion(Long id) {
        KnowledgeVersion version = KnowledgeVersion.uploaded(
                10L, "v1", "knowledge/refund.pdf", "refund.pdf",
                "application/pdf", 1024L, NOW.minusDays(1), 11L, NOW.minusDays(1));
        ReflectionTestUtils.setField(version, "id", id);
        version.startProcessing(NOW.minusHours(1));
        version.completeProcessing(NOW.minusMinutes(30));
        version.activate(NOW.minusMinutes(20));
        return version;
    }

    @Test
    void rejectsEvidenceOutsideRequestSnapshotBeforeSavingMessage() {
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.apply(command(), evidenceResult(102L), NOW))
                .isInstanceOf(com.chapchap.customer.global.error.custom.consultation.ConsultationStateException.class);
        verify(messageRepository, never()).save(any());
        verify(sourceRepository, never()).saveAll(any());
    }

    @Test
    void rejectsVersionDeactivatedWhileAiWasResponding() {
        KnowledgeVersion version = readyVersion(101L);
        version.deactivate(NOW);
        when(consultationRepository.findByIdForMessageWrite(501L)).thenReturn(Optional.of(consultation()));
        when(knowledgeVersionRepository.findAllById(List.of(101L))).thenReturn(List.of(version));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.apply(command(), evidenceResult(101L), NOW))
                .isInstanceOf(com.chapchap.customer.global.error.custom.consultation.ConsultationStateException.class);
        verify(messageRepository, never()).save(any());
    }

    private CustomerAiConsultationResult evidenceResult(long versionId) {
        return new CustomerAiConsultationResult(REQUEST_ID, CustomerAiConsultationResult.Decision.ANSWER,
                "정책 답변", CustomerAiConsultationResult.Route.POLICY, false, false,
                List.of(new CustomerAiConsultationResult.Evidence(versionId, "chunk-1", 1, 0.91)));
    }

    @Test
    void rejectsInvalidKnowledgeSnapshotsAndDefensivelyCopiesApprovedIds() {
        CustomerAiConsultationCommand original = command();
        for (List<Long> invalid : List.of(List.of(0L), List.of(-1L), List.of(101L, 101L),
                java.util.stream.LongStream.rangeClosed(1, 1001).boxed().toList())) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CustomerAiConsultationCommand(
                    original.requestId(), original.consultationId(), original.triggerMessageId(),
                    original.subject(), original.message(), original.conversationContext(), invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>(List.of(101L));
        CustomerAiConsultationCommand snapshot = new CustomerAiConsultationCommand(
                original.requestId(), original.consultationId(), original.triggerMessageId(),
                original.subject(), original.message(), original.conversationContext(), ids);
        ids.add(102L);
        assertThat(snapshot.knowledgeVersionIds()).containsExactly(101L);
    }
}
