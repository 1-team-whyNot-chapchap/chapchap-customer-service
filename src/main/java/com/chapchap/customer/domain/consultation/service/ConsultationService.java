package com.chapchap.customer.domain.consultation.service;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.domain.consultation.request.ConsultationCreateRequest;
import com.chapchap.customer.domain.consultation.response.ConsultationCreatedResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationMessageResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationMessagesResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationResponse;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultationService {
    private final ConsultationRepository consultationRepository;
    private final ConsultationMessageRepository consultationMessageRepository;

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

    private Consultation findMyConsultationEntity(Long userId, Long consultationId) {
        return consultationRepository.findByIdAndUserId(consultationId, userId)
                .orElseThrow(() -> new BusinessException(
                        CustomResponseCode.NOT_FOUND_RESOURCE_ERROR,
                        "상담을 찾을 수 없습니다."
                ));
    }
}
