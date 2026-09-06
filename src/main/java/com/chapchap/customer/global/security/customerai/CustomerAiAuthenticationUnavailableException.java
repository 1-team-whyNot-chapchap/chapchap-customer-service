package com.chapchap.customer.global.security.customerai;

public class CustomerAiAuthenticationUnavailableException extends RuntimeException {
    private static final String SAFE_MESSAGE = "Customer-AI internal authentication is unavailable.";

    public CustomerAiAuthenticationUnavailableException() {
        super(SAFE_MESSAGE);
    }
}
