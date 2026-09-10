package com.chapchap.customer.domain.customerai.dto.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class CustomerAiSubjectKeyMaterial {
    private static final int MINIMUM_RSA_BITS = 2048;

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    private CustomerAiSubjectKeyMaterial(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static CustomerAiSubjectKeyMaterial fromPem(String privateKeyPem, String publicKeyPem) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey parsedPrivate = factory.generatePrivate(
                    new PKCS8EncodedKeySpec(decodePem(privateKeyPem, "PRIVATE KEY")));
            PublicKey parsedPublic = factory.generatePublic(
                    new X509EncodedKeySpec(decodePem(publicKeyPem, "PUBLIC KEY")));
            if (!(parsedPrivate instanceof RSAPrivateKey rsaPrivate)
                    || !(parsedPublic instanceof RSAPublicKey rsaPublic)
                    || rsaPrivate.getModulus().bitLength() < MINIMUM_RSA_BITS
                    || rsaPublic.getModulus().bitLength() < MINIMUM_RSA_BITS) {
                throw new IllegalStateException("Customer-AI subject assertion requires RSA keys of at least 2048 bits.");
            }
            verifyPair(rsaPrivate, rsaPublic);
            return new CustomerAiSubjectKeyMaterial(rsaPrivate, rsaPublic);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Customer-AI subject assertion key configuration is invalid.", exception);
        }
    }

    private static byte[] decodePem(String value, String type) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Customer-AI subject assertion key configuration is missing.");
        }
        String normalized = value.replace("\\n", "\n")
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static void verifyPair(RSAPrivateKey privateKey, RSAPublicKey publicKey) throws Exception {
        byte[] probe = "chapchap-customer-ai-subject".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(probe);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(probe);
        if (!verifier.verify(signature)) {
            throw new IllegalStateException("Customer-AI subject assertion public and private keys do not match.");
        }
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }
}
