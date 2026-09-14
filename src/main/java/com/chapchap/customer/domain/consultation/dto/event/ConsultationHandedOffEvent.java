package com.chapchap.customer.domain.consultation.dto.event;

public record ConsultationHandedOffEvent(Long consultationId, int lastMessageSequenceNo) {
}
