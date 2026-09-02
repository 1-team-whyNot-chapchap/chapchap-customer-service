package com.chapchap.customer.domain.consultation.request;

import jakarta.validation.constraints.NotBlank;

public record ConsultationCreateRequest(
        @NotBlank String content
) {
}
