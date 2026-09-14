package com.chapchap.customer.domain.consultation.response.summary;

public record ConsultationHandoffSummaryResponse(Long consultationId, String status, String summary,
                                                 boolean retryAllowed) {
    public ConsultationHandoffSummaryResponse(Long consultationId, String status, String summary) {
        this(consultationId, status, summary, false);
    }
}
