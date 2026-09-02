package com.chapchap.customer.domain.consultation.response;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;

import java.time.LocalDateTime;

public record AdminConsultationResponse(Long consultationId, ConsultationStatus status, LocalDateTime escalatedAt,
                                        LocalDateTime createdAt) {
    public static AdminConsultationResponse from(Consultation consultation) {
        return new AdminConsultationResponse(consultation.getId(), consultation.getStatus(),
                consultation.getEscalatedAt(), consultation.getCreatedAt());
    }
}
