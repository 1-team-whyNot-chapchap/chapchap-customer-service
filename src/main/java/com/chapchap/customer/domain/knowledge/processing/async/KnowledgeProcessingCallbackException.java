package com.chapchap.customer.domain.knowledge.processing.async;

import java.util.Objects;

public final class KnowledgeProcessingCallbackException extends RuntimeException {
    private final Reason reason;

    public KnowledgeProcessingCallbackException(Reason reason) {
        super(Objects.requireNonNull(reason).message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        CONTRACT_ERROR("Customer-AI returned an invalid knowledge callback contract."),
        STATE_CONFLICT("Customer-AI knowledge callback conflicts with stored job state.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}