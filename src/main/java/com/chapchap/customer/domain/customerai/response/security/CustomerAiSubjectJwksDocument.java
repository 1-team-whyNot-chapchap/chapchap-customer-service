package com.chapchap.customer.domain.customerai.response.security;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public record CustomerAiSubjectJwksDocument(List<Jwk> keys) {
    public CustomerAiSubjectJwksDocument {
        keys = List.copyOf(keys);
    }

    public static CustomerAiSubjectJwksDocument from(String keyId, RSAPublicKey publicKey) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalStateException("Customer-AI subject assertion key ID must not be blank.");
        }
        return new CustomerAiSubjectJwksDocument(List.of(new Jwk(
                "RSA", "sig", "RS256", keyId.trim(),
                encode(publicKey.getModulus()), encode(publicKey.getPublicExponent()))));
    }

    private static String encode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Jwk(String kty, String use, String alg, String kid, String n, String e) {
    }
}
