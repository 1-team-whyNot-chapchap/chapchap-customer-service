package com.chapchap.customer.global.security.customerai;

import com.chapchap.customer.global.error.custom.customerai.CustomerAiCallbackAuthenticationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RemoteAuthJwksKeyResolverTest {
    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void resolvesAndCachesRs256VerificationKeyByKid() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth-service/.well-known/jwks.json"))
                .andRespond(withSuccess(jwks(), MediaType.APPLICATION_JSON));
        RemoteAuthJwksKeyResolver resolver = resolver(builder);

        assertThat(resolver.resolve("auth-1").getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(resolver.resolve("auth-1").getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        server.verify();
    }

    @Test
    void hidesJwksTransportFailureAndRejectsUnknownKid() {
        RestClient.Builder failingBuilder = RestClient.builder();
        MockRestServiceServer failingServer = MockRestServiceServer.bindTo(failingBuilder).build();
        failingServer.expect(requestTo("https://auth-service/.well-known/jwks.json"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> resolver(failingBuilder).resolve("auth-1"))
                .isExactlyInstanceOf(CustomerAiCallbackAuthenticationException.class)
                .satisfies(error -> assertThat(((CustomerAiCallbackAuthenticationException) error).reason())
                        .isEqualTo(CustomerAiCallbackAuthenticationException.Reason.KEY_UNAVAILABLE));
        failingServer.verify();

        RestClient.Builder unknownBuilder = RestClient.builder();
        MockRestServiceServer unknownServer = MockRestServiceServer.bindTo(unknownBuilder).build();
        unknownServer.expect(requestTo("https://auth-service/.well-known/jwks.json"))
                .andRespond(withSuccess(jwks(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> resolver(unknownBuilder).resolve("other-key"))
                .isExactlyInstanceOf(CustomerAiCallbackAuthenticationException.class)
                .satisfies(error -> assertThat(((CustomerAiCallbackAuthenticationException) error).reason())
                        .isEqualTo(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN));
        unknownServer.verify();
    }

    @Test
    void refreshesCachedJwksOnceWhenARotatedKidAppears() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair rotated = generator.generateKeyPair();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://auth-service/.well-known/jwks.json"))
                .andRespond(withSuccess(jwks(keyPair, "auth-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://auth-service/.well-known/jwks.json"))
                .andRespond(withSuccess(jwks(rotated, "auth-2"), MediaType.APPLICATION_JSON));
        RemoteAuthJwksKeyResolver resolver = resolver(builder);

        assertThat(resolver.resolve("auth-1").getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(resolver.resolve("auth-2").getEncoded()).isEqualTo(rotated.getPublic().getEncoded());
        server.verify();
    }

    private RemoteAuthJwksKeyResolver resolver(RestClient.Builder builder) {
        return new RemoteAuthJwksKeyResolver(
                builder.baseUrl("https://auth-service/.well-known/jwks.json").build(),
                new ObjectMapper(),
                Duration.ofMinutes(5),
                Clock.fixed(Instant.parse("2026-09-07T06:00:00Z"), ZoneOffset.UTC));
    }

    private String jwks() {
        return jwks(keyPair, "auth-1");
    }

    private String jwks(KeyPair pair, String keyId) {
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        return """
                {"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"%s","n":"%s","e":"%s"}]}
                """.formatted(keyId, unsigned(publicKey.getModulus()), unsigned(publicKey.getPublicExponent()));
    }

    private String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }
}
