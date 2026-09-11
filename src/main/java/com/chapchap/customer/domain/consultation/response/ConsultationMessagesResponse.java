package com.chapchap.customer.domain.consultation.response;

import java.util.List;

public record ConsultationMessagesResponse(
        Long consultationId,
        List<ConsultationMessageResponse> messages
) {
}
