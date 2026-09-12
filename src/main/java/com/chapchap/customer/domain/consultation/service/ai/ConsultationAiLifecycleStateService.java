package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationCommand;
import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationResult;
import com.chapchap.customer.domain.consultation.dto.ai.PreparedConsultationAiRequest;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessageSource;
import com.chapchap.customer.domain.consultation.constant.ConsultationSenderType;
import com.chapchap.customer.domain.consultation.constant.ConsultationStatus;
import com.chapchap.customer.domain.consultation.dto.event.ConsultationAiResponseRequestedEvent;
import com.chapchap.customer.domain.consultation.dto.event.ConsultationMessageSavedEvent;
import com.chapchap.customer.domain.consultation.dto.event.ConsultationHandedOffEvent;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageSourceRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.domain.consultation.response.ConsultationMessageResponse;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.global.exception.consultation.ConsultationNotFoundException;
import com.chapchap.customer.global.exception.consultation.ConsultationStateException;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;
import com.chapchap.customer.domain.customerai.constant.security.CustomerAiSubjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.ai.consultation-response",
        name = "async-enabled",
        havingValue = "true"
)
public class ConsultationAiLifecycleStateService {
    private static final int MAX_CONTEXT_MESSAGES = 20;

    private final ConsultationRepository consultationRepository;
    private final ConsultationMessageRepository messageRepository;
    private final ConsultationMessageSourceRepository sourceRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final AuditLogWriter auditLogWriter;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Optional<PreparedConsultationAiRequest> prepare(
            ConsultationAiResponseRequestedEvent event,
            UUID requestId
    ) {
        Consultation consultation = consultationRepository.findByIdForMessageWrite(event.consultationId())
                .orElseThrow(ConsultationNotFoundException::new);
        if (consultation.getStatus() != ConsultationStatus.AI_HANDLING
                || !consultation.getUserId().equals(event.userId())
                || messageRepository.findByTriggerMessageId(event.triggerMessageId()).isPresent()) {
            return Optional.empty();
        }

        ConsultationMessage trigger = messageRepository.findById(event.triggerMessageId())
                .filter(message -> message.getConsultationId().equals(event.consultationId()))
                .filter(message -> message.getSenderType() == ConsultationSenderType.USER)
                .filter(message -> event.userId().equals(message.getSenderUserId()))
                .orElseThrow(() -> new ConsultationStateException("AI 상담 요청 메시지 문맥이 올바르지 않습니다."));

        List<ConsultationMessage> messages = messageRepository
                .findByConsultation_IdOrderBySequenceNoAsc(event.consultationId());
        int triggerIndex = messages.indexOf(trigger);
        if (triggerIndex < 0) {
            throw new ConsultationStateException("AI 상담 요청 메시지 순서를 확인할 수 없습니다.");
        }
        int contextStart = Math.max(0, triggerIndex - MAX_CONTEXT_MESSAGES);
        List<String> context = messages.subList(contextStart, triggerIndex)
                .stream()
                .map(ConsultationMessage::getContent)
                .toList();

        CustomerAiSubjectAssertionRequest subject = new CustomerAiSubjectAssertionRequest(
                event.userId(),
                event.role(),
                List.of(CustomerAiSubjectScope.POLICY_READ.value()),
                requestId,
                event.consultationId()
        );
        return Optional.of(new PreparedConsultationAiRequest(new CustomerAiConsultationCommand(
                requestId,
                event.consultationId(),
                event.triggerMessageId(),
                subject,
                trigger.getContent(),
                context,
                knowledgeVersionRepository.findApprovedVersionIds(
                        KnowledgeProcessingStatus.READY, LocalDateTime.now(ZoneId.of("Asia/Seoul")))
        )));
    }

    @Transactional
    public boolean apply(
            CustomerAiConsultationCommand command,
            CustomerAiConsultationResult result,
            LocalDateTime now
    ) {
        if (!command.requestId().equals(result.requestId())) {
            throw new ConsultationStateException("Customer-AI 응답 requestId가 요청과 일치하지 않습니다.");
        }
        if (result.route() == CustomerAiConsultationResult.Route.USER_STATE
                || result.route() == CustomerAiConsultationResult.Route.POLICY_AND_STATE) {
            throw new ConsultationStateException("Current-State 경로는 아직 활성화되지 않았습니다.");
        }
        Consultation consultation = consultationRepository.findByIdForMessageWrite(command.consultationId())
                .orElseThrow(ConsultationNotFoundException::new);
        if (consultation.getStatus() != ConsultationStatus.AI_HANDLING
                || messageRepository.findByTriggerMessageId(command.triggerMessageId()).isPresent()) {
            return false;
        }

        ConsultationMessage aiMessage = null;
        if (result.decision() != CustomerAiConsultationResult.Decision.HANDOFF) {
            validateEvidence(command, result.evidence(), now);
            int nextSequence = messageRepository
                    .findTopByConsultation_IdOrderBySequenceNoDesc(command.consultationId())
                    .map(message -> message.getSequenceNo() + 1)
                    .orElse(1);
            aiMessage = messageRepository.save(ConsultationMessage.aiResponse(
                    consultation,
                    result.answer(),
                    nextSequence,
                    command.triggerMessageId(),
                    now
            ));
            saveEvidence(aiMessage, result.evidence(), now);
            eventPublisher.publishEvent(new ConsultationMessageSavedEvent(
                    consultation.getId(), ConsultationMessageResponse.from(aiMessage)));
        }

        if (result.requiresAdminHandoff()) {
            handoff(consultation, result.decision().name(), now);
        }
        return true;
    }

    @Transactional
    public boolean handoffAfterFailure(
            CustomerAiConsultationCommand command,
            String reason,
            LocalDateTime now
    ) {
        Consultation consultation = consultationRepository.findByIdForMessageWrite(command.consultationId())
                .orElseThrow(ConsultationNotFoundException::new);
        if (consultation.getStatus() != ConsultationStatus.AI_HANDLING
                || messageRepository.findByTriggerMessageId(command.triggerMessageId()).isPresent()) {
            return false;
        }
        handoff(consultation, reason, now);
        return true;
    }

    @Transactional
    public boolean handoffAfterPreparationFailure(
            ConsultationAiResponseRequestedEvent event,
            String reason,
            LocalDateTime now
    ) {
        Consultation consultation = consultationRepository.findByIdForMessageWrite(event.consultationId())
                .orElseThrow(ConsultationNotFoundException::new);
        if (consultation.getStatus() != ConsultationStatus.AI_HANDLING
                || !consultation.getUserId().equals(event.userId())) {
            return false;
        }
        handoff(consultation, reason, now);
        return true;
    }

    private void saveEvidence(
            ConsultationMessage aiMessage,
            List<CustomerAiConsultationResult.Evidence> evidence,
            LocalDateTime now
    ) {
        if (evidence.isEmpty()) {
            return;
        }
        Map<Long, KnowledgeVersion> versions = new HashMap<>();
        knowledgeVersionRepository.findAllById(evidence.stream()
                        .map(CustomerAiConsultationResult.Evidence::knowledgeVersionId)
                        .distinct()
                        .toList())
                .forEach(version -> versions.put(version.getId(), version));

        List<ConsultationMessageSource> sources = evidence.stream()
                .map(item -> {
                    KnowledgeVersion version = versions.get(item.knowledgeVersionId());
                    if (!isApproved(version, now)) {
                        throw new ConsultationStateException("AI 답변 Knowledge Version을 검증할 수 없습니다.");
                    }
                    return ConsultationMessageSource.create(
                            aiMessage,
                            version,
                            item.chunkId(),
                            item.retrievalRank(),
                            item.retrievalScore(),
                            now
                    );
                })
                .toList();
        sourceRepository.saveAll(sources);
    }

    private void handoff(Consultation consultation, String reason, LocalDateTime now) {
        String beforeStatus = consultation.getStatus().name();
        if (consultation.requestAdminHandoff(now)) {
            auditLogWriter.recordConsultationAiEscalated(consultation, beforeStatus, reason, now);
            int cutoff = messageRepository.findTopByConsultation_IdOrderBySequenceNoDesc(consultation.getId())
                    .map(ConsultationMessage::getSequenceNo).orElse(0);
            eventPublisher.publishEvent(new ConsultationHandedOffEvent(consultation.getId(), cutoff));
        }
    }

    private boolean isApproved(KnowledgeVersion version, LocalDateTime now) {
        return version != null && version.getProcessingStatus() == KnowledgeProcessingStatus.READY
                && version.isActive() && !version.getEffectiveFrom().isAfter(now);
    }

    private void validateEvidence(CustomerAiConsultationCommand command,
            List<CustomerAiConsultationResult.Evidence> evidence, LocalDateTime now) {
        List<Long> ids = evidence.stream()
                .map(CustomerAiConsultationResult.Evidence::knowledgeVersionId).distinct().toList();
        if (!command.knowledgeVersionIds().containsAll(ids)) {
            throw new ConsultationStateException("AI 답변에 승인되지 않은 Knowledge Version이 포함되어 있습니다.");
        }
        if (ids.isEmpty()) {
            return;
        }
        List<KnowledgeVersion> versions = knowledgeVersionRepository.findAllById(ids);
        if (versions.size() != ids.size() || versions.stream().anyMatch(v -> !isApproved(v, now))) {
            throw new ConsultationStateException("AI 답변 Knowledge Version이 현재 활성 상태가 아닙니다.");
        }
    }
}
