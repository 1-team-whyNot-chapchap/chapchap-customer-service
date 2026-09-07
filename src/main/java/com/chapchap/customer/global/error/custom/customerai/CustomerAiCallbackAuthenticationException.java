package com.chapchap.customer.global.error.custom.customerai;

public final class CustomerAiCallbackAuthenticationException extends RuntimeException {
    private final Reason reason;

    public CustomerAiCallbackAuthenticationException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        MISSING_CREDENTIALS("Customer-AI callback credentials are required."),
        INVALID_TOKEN("Customer-AI callback credentials are invalid."),
        KEY_UNAVAILABLE("Customer-AI callback verification key is unavailable."),
        FORBIDDEN_SERVICE("Customer-AI callback service is not allowed.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}
