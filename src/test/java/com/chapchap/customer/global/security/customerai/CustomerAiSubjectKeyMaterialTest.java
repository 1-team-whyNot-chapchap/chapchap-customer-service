package com.chapchap.customer.global.security.customerai;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiSubjectKeyMaterialTest {
    private static KeyPair first;
    private static KeyPair second;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        first = generator.generateKeyPair();
        second = generator.generateKeyPair();
    }

    @Test
    void loadsMatchingPemKeysAndPublishesOnlyPublicJwkValues() {
        CustomerAiSubjectKeyMaterial material = CustomerAiSubjectKeyMaterial.fromPem(
                pem("PRIVATE KEY", first.getPrivate().getEncoded()),
                pem("PUBLIC KEY", first.getPublic().getEncoded()));

        CustomerAiSubjectJwksDocument document = CustomerAiSubjectJwksDocument.from(
                "customer-subject-1", material.publicKey());

        assertThat(material.privateKey()).isEqualTo(first.getPrivate());
        assertThat(document.keys()).singleElement().satisfies(key -> {
            assertThat(key.kty()).isEqualTo("RSA");
            assertThat(key.use()).isEqualTo("sig");
            assertThat(key.alg()).isEqualTo("RS256");
            assertThat(key.kid()).isEqualTo("customer-subject-1");
            assertThat(key.n()).isNotBlank();
            assertThat(key.e()).isNotBlank();
            assertThat(key.toString()).doesNotContain("PRIVATE", "secret");
        });
    }

    @Test
    void rejectsMismatchedOrMissingKeyMaterial() {
        assertThatThrownBy(() -> CustomerAiSubjectKeyMaterial.fromPem(
                pem("PRIVATE KEY", first.getPrivate().getEncoded()),
                pem("PUBLIC KEY", second.getPublic().getEncoded())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("do not match");

        assertThatThrownBy(() -> CustomerAiSubjectKeyMaterial.fromPem("", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("BEGIN");
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}
