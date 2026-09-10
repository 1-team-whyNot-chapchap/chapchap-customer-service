package com.chapchap.customer.domain.customerai.service.security;

public final class CustomerAiRequestCredentials {
    private final String authorization;
    private final String subjectAssertion;

    CustomerAiRequestCredentials(String authorization, String subjectAssertion) {
        this.authorization = authorization;
        this.subjectAssertion = subjectAssertion;
    }

    public String authorization() {
        return authorization;
    }

    public String subjectAssertion() {
        return subjectAssertion;
    }

    @Override
    public String toString() {
        return "CustomerAiRequestCredentials[authorization=[REDACTED], subjectAssertion=[REDACTED]]";
    }
}
