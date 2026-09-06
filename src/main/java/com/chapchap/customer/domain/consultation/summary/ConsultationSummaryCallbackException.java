package com.chapchap.customer.domain.consultation.summary;

import java.util.Objects;

public final class ConsultationSummaryCallbackException extends RuntimeException {
    private final Reason reason;

    public ConsultationSummaryCallbackException(Reason reason) {
        super(Objects.requireNonNull(reason).message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        CONTRACT_ERROR("Customer-AI returned an invalid consultation summary callback contract."),
        STATE_CONFLICT("Customer-AI consultation summary callback conflicts with stored job state.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}