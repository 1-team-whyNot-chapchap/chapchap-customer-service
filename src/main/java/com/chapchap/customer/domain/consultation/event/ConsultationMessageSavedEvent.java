package com.chapchap.customer.domain.consultation.event;

import com.chapchap.customer.domain.consultation.response.ConsultationMessageResponse;

public record ConsultationMessageSavedEvent(
        Long consultationId,
        ConsultationMessageResponse message
) {
}
