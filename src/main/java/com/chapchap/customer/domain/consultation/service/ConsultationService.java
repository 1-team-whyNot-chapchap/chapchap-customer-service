package com.chapchap.customer.domain.consultation.service;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.entity.ConsultationSenderType;
import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;
import com.chapchap.customer.domain.consultation.event.ConsultationMessageSavedEvent;
import com.chapchap.customer.domain.consultation.request.ConsultationRealtimeMessageRequest;
import com.chapchap.customer.domain.consultation.response.AdminConsultationResponse;
import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.domain.consultation.request.ConsultationCreateRequest;
import com.chapchap.customer.domain.consultation.response.ConsultationCreatedResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationMessageResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationMessagesResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationResponse;
import com.chapchap.customer.global.error.custom.consultation.ConsultationNotFoundException;
import com.chapchap.customer.global.error.custom.consultation.ConsultationStateException;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultationService {
    private final ConsultationRepository consultationRepository;
    private final ConsultationMessageRepository consultationMessageRepository;
    private final AuditLogWriter auditLogWriter;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public ConsultationCreatedResponse createConsultation(Long userId, ConsultationCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Consultation consultation = consultationRepository.save(Consultation.create(userId, now));
        ConsultationMessage firstMessage = consultationMessageRepository.save(
                ConsultationMessage.firstUserMessage(consultation, userId, request.content().trim(), now)
        );

        return ConsultationCreatedResponse.of(consultation, ConsultationMessageResponse.from(firstMessage));
    }

    @Transactional(readOnly = true)
    public ConsultationResponse findMyConsultation(Long userId, Long consultationId) {
        return ConsultationResponse.from(findMyConsultationEntity(userId, consultationId));
    }

    @Transactional(readOnly = true)
    public ConsultationMessagesResponse findMyConsultationMessages(Long userId, Long consultationId) {
        Consultation consultation = findMyConsultationEntity(userId, consultationId);
        List<ConsultationMessageResponse> messages = consultationMessageRepository
                .findByConsultation_IdOrderBySequenceNoAsc(consultation.getId())
                .stream()
                .map(ConsultationMessageResponse::from)
                .toList();
        return new ConsultationMessagesResponse(consultation.getId(), messages);
    }

    @Transactional
    public ConsultationResponse requestAdminHandoff(Long userId, Long consultationId) {
        Consultation consultation = findMyConsultationEntity(userId, consultationId);
        String beforeStatus = consultation.getStatus().name();
        if (consultation.requestAdminHandoff(LocalDateTime.now())) {
            auditLogWriter.recordConsultationEscalated(userId, consultation, beforeStatus, consultation.getUpdatedAt());
        }
        return ConsultationResponse.from(consultation);
    }

    @Transactional(readOnly = true)
    public List<AdminConsultationResponse> findWaitingConsultations() {
        return consultationRepository.findByStatusOrderByCreatedAtAsc(ConsultationStatus.WAITING_ADMIN)
                .stream().map(AdminConsultationResponse::from).toList();
    }

    @Transactional
    public ConsultationResponse acceptConsultation(Long adminId, Long consultationId) {
        LocalDateTime now = LocalDateTime.now();
        if (consultationRepository.acceptWaitingConsultation(consultationId, adminId, now) != 1) {
            if (!consultationRepository.existsById(consultationId)) {
                throw new ConsultationNotFoundException();
            }
            throw new ConsultationStateException("상담을 수락할 수 없는 상태입니다.");
        }
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(ConsultationNotFoundException::new);
        auditLogWriter.recordConsultationAccepted(adminId, consultation, ConsultationStatus.WAITING_ADMIN.name(), now);
        return ConsultationResponse.from(consultation);
    }

    @Transactional
    public ConsultationMessageResponse saveRealtimeMessage(
            GatewayUserPrincipal principal,
            Long consultationId,
            ConsultationRealtimeMessageRequest request
    ) {
        Consultation consultation = consultationRepository.findByIdForMessageWrite(consultationId)
                .orElseThrow(ConsultationNotFoundException::new);
        Long senderUserId = requireUserId(principal);
        ConsultationSenderType senderType = resolveMessageSenderType(principal, consultation, senderUserId);

        if (consultation.getStatus() != ConsultationStatus.IN_PROGRESS) {
            throw new ConsultationStateException("진행 중인 상담에서만 메시지를 보낼 수 있습니다.");
        }

        int nextSequenceNo = consultationMessageRepository
                .findTopByConsultation_IdOrderBySequenceNoDesc(consultationId)
                .map(message -> message.getSequenceNo() + 1)
                .orElse(1);
        LocalDateTime now = LocalDateTime.now();
        ConsultationMessage savedMessage = consultationMessageRepository.save(ConsultationMessage.create(
                consultation,
                senderType,
                senderUserId,
                request.content().trim(),
                nextSequenceNo,
                now
        ));
        ConsultationMessageResponse response = ConsultationMessageResponse.from(savedMessage);
        applicationEventPublisher.publishEvent(new ConsultationMessageSavedEvent(consultationId, response));
        return response;
    }

    @Transactional(readOnly = true)
    public void assertWebSocketParticipant(Long consultationId, GatewayUserPrincipal principal) {
        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(ConsultationNotFoundException::new);
        Long userId = requireUserId(principal);
        resolveMessageSenderType(principal, consultation, userId);
    }

    private Consultation findMyConsultationEntity(Long userId, Long consultationId) {
        return consultationRepository.findByIdAndUserId(consultationId, userId)
                .orElseThrow(ConsultationNotFoundException::new);
    }

    private ConsultationSenderType resolveMessageSenderType(
            GatewayUserPrincipal principal,
            Consultation consultation,
            Long userId
    ) {
        if ((principal.role() == RolePolicy.CUSTOMER || principal.role() == RolePolicy.RIDER)
                && consultation.getUserId().equals(userId)) {
            return ConsultationSenderType.USER;
        }
        if ((principal.role() == RolePolicy.ADMIN || principal.role() == RolePolicy.SUPER_ADMIN)
                && consultation.getAssignedAdminId() != null
                && consultation.getAssignedAdminId().equals(userId)) {
            return ConsultationSenderType.ADMIN;
        }
        throw new AccessDeniedException("상담 참여자만 접근할 수 있습니다.");
    }

    private Long requireUserId(GatewayUserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        try {
            return Long.parseLong(principal.userId());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("유효하지 않은 사용자 ID입니다.");
        }
    }
}
