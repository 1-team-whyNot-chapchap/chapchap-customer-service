package com.chapchap.customer.domain.customerai.service.security;

import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;

@FunctionalInterface
public interface CustomerAiSubjectAssertionIssuer {
    String issue(CustomerAiSubjectAssertionRequest request);
}
