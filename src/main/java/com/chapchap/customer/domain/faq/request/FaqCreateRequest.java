package com.chapchap.customer.domain.faq.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaqCreateRequest(
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 500) String question,
        @NotBlank String answer,
        @Min(0) int displayOrder,
        @NotNull Boolean published
) {
}
