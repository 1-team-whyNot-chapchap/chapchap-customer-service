package com.chapchap.customer.global.security.customerai;

@FunctionalInterface
public interface CustomerAiSubjectAssertionIssuer {
    String issue(CustomerAiSubjectAssertionRequest request);
}
