package com.chapchap.customer.global.security.customerai;

import java.security.PublicKey;

@FunctionalInterface
public interface CustomerAiCallbackVerificationKeyResolver {
    PublicKey resolve(String keyId);
}
