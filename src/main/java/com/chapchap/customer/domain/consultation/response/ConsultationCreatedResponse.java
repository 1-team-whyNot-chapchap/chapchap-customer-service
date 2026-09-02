package com.chapchap.customer.domain.consultation.response;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;

import java.time.LocalDateTime;

public record ConsultationCreatedResponse(
        Long consultationId,
        ConsultationStatus status,
        LocalDateTime createdAt,
        ConsultationMessageResponse initialMessage
) {
    public static ConsultationCreatedResponse of(Consultation consultation, ConsultationMessageResponse initialMessage) {
        return new ConsultationCreatedResponse(
                consultation.getId(),
                consultation.getStatus(),
                consultation.getCreatedAt(),
                initialMessage
        );
    }
}
