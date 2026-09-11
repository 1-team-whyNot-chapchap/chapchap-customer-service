package com.chapchap.customer.domain.consultation.dto.ai;

import java.util.Objects;

public record PreparedConsultationAiRequest(CustomerAiConsultationCommand command) {
    public PreparedConsultationAiRequest {
        Objects.requireNonNull(command);
    }
}
