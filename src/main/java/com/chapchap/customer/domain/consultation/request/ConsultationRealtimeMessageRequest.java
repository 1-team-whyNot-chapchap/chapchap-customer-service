package com.chapchap.customer.domain.consultation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConsultationRealtimeMessageRequest(
        @NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
        @Size(max = 2000, message = "메시지는 2,000자 이하여야 합니다.")
        String content
) {
}
