package com.chapchap.customer.domain.consultation.dto.summary;

public record ConsultationSummaryCallback(
        long summaryJobId,
        long consultationId,
        Status status,
        String summary,
        FailureCode failureCode,
        Boolean retryable
) {
    public enum Status {
        COMPLETED,
        FAILED
    }

    public enum FailureCode {
        UNSAFE_CONTEXT,
        SUMMARY_GENERATION_FAILED,
        LLM_UNAVAILABLE,
        PROCESSING_TIMEOUT,
        CUSTOMER_AI_UNAVAILABLE
    }
}