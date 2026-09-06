package com.chapchap.customer.global.security.customerai;

import java.util.Objects;

public final class CustomerAiRequestCredentialsProvider {
    private static final String BEARER_PREFIX = "Bearer ";

    private final CustomerAiServiceTokenProvider serviceTokenProvider;
    private final CustomerAiSubjectAssertionIssuer subjectAssertionIssuer;

    public CustomerAiRequestCredentialsProvider(
            CustomerAiServiceTokenProvider serviceTokenProvider,
            CustomerAiSubjectAssertionIssuer subjectAssertionIssuer
    ) {
        this.serviceTokenProvider = Objects.requireNonNull(serviceTokenProvider);
        this.subjectAssertionIssuer = Objects.requireNonNull(subjectAssertionIssuer);
    }

    public CustomerAiRequestCredentials create(CustomerAiSubjectAssertionRequest request) {
        Objects.requireNonNull(request, "request must not be null.");
        String serviceToken;
        try {
            serviceToken = serviceTokenProvider.getServiceToken();
        } catch (RuntimeException exception) {
            throw new CustomerAiAuthenticationUnavailableException();
        }
        if (!isCompactJwt(serviceToken)) {
            throw new CustomerAiAuthenticationUnavailableException();
        }

        String assertion = subjectAssertionIssuer.issue(request);
        if (!isCompactJwt(assertion)) {
            throw new CustomerAiAuthenticationUnavailableException();
        }
        return new CustomerAiRequestCredentials(BEARER_PREFIX + serviceToken, assertion);
    }

    private boolean isCompactJwt(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        String[] segments = value.split("\\.", -1);
        return segments.length == 3
                && !segments[0].isEmpty()
                && !segments[1].isEmpty()
                && !segments[2].isEmpty();
    }
}
