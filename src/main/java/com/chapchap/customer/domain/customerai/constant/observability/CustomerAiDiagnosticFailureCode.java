package com.chapchap.customer.domain.customerai.constant.observability;

public enum CustomerAiDiagnosticFailureCode {
    REQUEST_REJECTED,
    AUTHENTICATION_REJECTED,
    AUTHENTICATION_UNAVAILABLE,
    FORBIDDEN,
    IDEMPOTENCY_CONFLICT,
    UNSAFE_RESPONSE,
    DEPENDENCY_UNAVAILABLE,
    TIMEOUT,
    CONTRACT_ERROR,
    STATE_CONFLICT,
    SOURCE_FETCH_FAILED,
    TEXT_EXTRACTION_FAILED,
    UNSUPPORTED_DOCUMENT,
    ENCRYPTED_DOCUMENT,
    CHUNK_PROFILE_INVALID,
    EMBEDDING_UNAVAILABLE,
    VECTOR_STORE_UNAVAILABLE,
    PROCESSING_TIMEOUT,
    CUSTOMER_AI_UNAVAILABLE,
    UNSAFE_CONTEXT,
    SUMMARY_GENERATION_FAILED,
    LLM_UNAVAILABLE;

    public static CustomerAiDiagnosticFailureCode from(Enum<?> value) {
        if (value == null) {
            return CONTRACT_ERROR;
        }
        try {
            return valueOf(value.name());
        } catch (IllegalArgumentException exception) {
            return CONTRACT_ERROR;
        }
    }
}
