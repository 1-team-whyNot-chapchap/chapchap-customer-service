package com.chapchap.customer.global.config;

import com.chapchap.customer.global.security.customerai.CustomerAiAuthenticationUnavailableException;
import com.chapchap.customer.global.security.customerai.CustomerAiRequestCredentialsProvider;
import com.chapchap.customer.global.security.customerai.CustomerAiServiceTokenProvider;
import com.chapchap.customer.global.security.customerai.CustomerAiSubjectJwksDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiInternalAuthConfigurationTest {
    private static String privateKeyPem;
    private static String publicKeyPem;

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKeyPem = escapedPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        publicKeyPem = escapedPem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    @Test
    void disabledConfigurationProvidesOnlyFailClosedTokenProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(CustomerAiInternalAuthConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CustomerAiServiceTokenProvider.class);
                    assertThat(context).doesNotHaveBean(CustomerAiRequestCredentialsProvider.class);
                    assertThat(context).doesNotHaveBean(CustomerAiSubjectJwksDocument.class);
                    assertThatThrownBy(() -> context.getBean(CustomerAiServiceTokenProvider.class)
                            .getServiceToken())
                            .isExactlyInstanceOf(CustomerAiAuthenticationUnavailableException.class);
                });
    }

    @Test
    void enabledConfigurationBuildsTokenAssertionAndJwksRuntime() {
        new ApplicationContextRunner()
                .withUserConfiguration(CustomerAiInternalAuthConfiguration.class)
                .withPropertyValues(
                        "customer.ai.internal-auth.enabled=true",
                        "customer.ai.internal-auth.auth-base-url=https://auth.internal",
                        "customer.ai.internal-auth.client-secret=test-secret",
                        "customer.ai.internal-auth.subject-private-key-pem=" + privateKeyPem,
                        "customer.ai.internal-auth.subject-public-key-pem=" + publicKeyPem)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CustomerAiServiceTokenProvider.class);
                    assertThat(context).hasSingleBean(CustomerAiRequestCredentialsProvider.class);
                    assertThat(context).hasSingleBean(CustomerAiSubjectJwksDocument.class);
                });
    }

    private static String escapedPem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\\n"
                + Base64.getEncoder().encodeToString(encoded)
                + "\\n-----END " + type + "-----";
    }
}
