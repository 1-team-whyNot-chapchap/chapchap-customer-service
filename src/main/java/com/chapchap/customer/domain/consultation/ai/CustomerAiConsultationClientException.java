package com.chapchap.customer.domain.consultation.ai;

public final class CustomerAiConsultationClientException extends RuntimeException {
    private final Reason reason;

    public CustomerAiConsultationClientException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public boolean retryable() {
        return reason == Reason.DEPENDENCY_UNAVAILABLE || reason == Reason.TIMEOUT;
    }

    public enum Reason {
        REQUEST_REJECTED("Customer-AI rejected the consultation request."),
        AUTHENTICATION_REJECTED("Customer-AI authentication was rejected."),
        IDEMPOTENCY_CONFLICT("Customer-AI rejected a conflicting consultation request."),
        UNSAFE_RESPONSE("Customer-AI could not produce a safe consultation response."),
        DEPENDENCY_UNAVAILABLE("Customer-AI consultation is unavailable."),
        TIMEOUT("Customer-AI consultation timed out."),
        CONTRACT_ERROR("Customer-AI returned an invalid consultation contract.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}
