package com.chapchap.customer.domain.customerai.service.security;

import java.security.PublicKey;

@FunctionalInterface
public interface CustomerAiCallbackVerificationKeyResolver {
    PublicKey resolve(String keyId);
}
