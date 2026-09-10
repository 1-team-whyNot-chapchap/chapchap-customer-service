package com.chapchap.customer.global.exception.consultation.summary;

import java.util.Objects;

public final class CustomerAiConsultationSummaryClientException extends RuntimeException {
    private final Reason reason;

    public CustomerAiConsultationSummaryClientException(Reason reason) {
        super(Objects.requireNonNull(reason).message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public boolean retryable() {
        return reason == Reason.DEPENDENCY_UNAVAILABLE || reason == Reason.TIMEOUT;
    }

    public enum Reason {
        REQUEST_REJECTED("Customer-AI rejected the consultation summary request."),
        AUTHENTICATION_REJECTED("Customer-AI authentication was rejected."),
        AUTHENTICATION_UNAVAILABLE("Customer-AI authentication is unavailable."),
        IDEMPOTENCY_CONFLICT("Customer-AI rejected a conflicting consultation summary job."),
        DEPENDENCY_UNAVAILABLE("Customer-AI consultation summary is unavailable."),
        TIMEOUT("Customer-AI consultation summary submission timed out."),
        CONTRACT_ERROR("Customer-AI returned an invalid consultation summary contract.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}