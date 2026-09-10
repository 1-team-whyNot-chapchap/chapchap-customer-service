package com.chapchap.customer.global.exception.customerai.security;

public class CustomerAiAuthenticationUnavailableException extends RuntimeException {
    private static final String SAFE_MESSAGE = "Customer-AI internal authentication is unavailable.";

    public CustomerAiAuthenticationUnavailableException() {
        super(SAFE_MESSAGE);
    }
}
