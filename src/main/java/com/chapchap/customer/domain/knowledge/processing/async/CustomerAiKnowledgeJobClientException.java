package com.chapchap.customer.domain.knowledge.processing.async;

import java.util.Objects;

public final class CustomerAiKnowledgeJobClientException extends RuntimeException {
    private final Reason reason;

    public CustomerAiKnowledgeJobClientException(Reason reason) {
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
        REQUEST_REJECTED("Customer-AI rejected the knowledge job request."),
        AUTHENTICATION_REJECTED("Customer-AI authentication was rejected."),
        AUTHENTICATION_UNAVAILABLE("Customer-AI authentication is unavailable."),
        IDEMPOTENCY_CONFLICT("Customer-AI rejected a conflicting knowledge job."),
        DEPENDENCY_UNAVAILABLE("Customer-AI knowledge processing is unavailable."),
        TIMEOUT("Customer-AI knowledge job submission timed out."),
        CONTRACT_ERROR("Customer-AI returned an invalid knowledge job contract.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}