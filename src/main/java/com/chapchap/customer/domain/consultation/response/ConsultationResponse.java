package com.chapchap.customer.domain.consultation.response;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;

import java.time.LocalDateTime;

public record ConsultationResponse(
        Long consultationId,
        ConsultationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime escalatedAt,
        Long assignedAdminId,
        LocalDateTime assignedAt
) {
    public static ConsultationResponse from(Consultation consultation) {
        return new ConsultationResponse(
                consultation.getId(),
                consultation.getStatus(),
                consultation.getCreatedAt(),
                consultation.getUpdatedAt(),
                consultation.getEscalatedAt(),
                consultation.getAssignedAdminId(),
                consultation.getAssignedAt()
        );
    }
}
