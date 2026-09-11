package com.chapchap.customer.domain.customerai.service.security;

import com.chapchap.customer.global.exception.customerai.CustomerAiCallbackAuthenticationException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiCallbackJwtVerifierTest {
    private static final Instant NOW = Instant.parse("2026-09-07T06:00:00Z");
    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void verifiesAuthIssuedCustomerAiCallbackToken() {
        verifier(keyId -> keyPair.getPublic()).verify("Bearer " + token(
                "chapchap-customer-service", "customer-ai", "customer-ai.callback", 300));
    }

    @Test
    void rejectsMissingMalformedAndTamperedCredentials() {
        CustomerAiCallbackJwtVerifier verifier = verifier(keyId -> keyPair.getPublic());

        assertReason(() -> verifier.verify(null),
                CustomerAiCallbackAuthenticationException.Reason.MISSING_CREDENTIALS);
        assertReason(() -> verifier.verify("Basic credential"),
                CustomerAiCallbackAuthenticationException.Reason.MISSING_CREDENTIALS);
        assertReason(() -> verifier.verify("Bearer header.payload.signature"),
                CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
    }

    @Test
    void rejectsWrongAudienceSubjectScopeAndExcessiveLifetime() {
        CustomerAiCallbackJwtVerifier verifier = verifier(keyId -> keyPair.getPublic());

        assertReason(() -> verifier.verify("Bearer " + token(
                        "other-service", "customer-ai", "customer-ai.callback", 300)),
                CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
        assertReason(() -> verifier.verify("Bearer " + token(
                        "chapchap-customer-service", "other-service", "customer-ai.callback", 300)),
                CustomerAiCallbackAuthenticationException.Reason.FORBIDDEN_SERVICE);
        assertReason(() -> verifier.verify("Bearer " + token(
                        "chapchap-customer-service", "customer-ai", "customer-ai.invoke", 300)),
                CustomerAiCallbackAuthenticationException.Reason.FORBIDDEN_SERVICE);
        assertReason(() -> verifier.verify("Bearer " + token(
                        "chapchap-customer-service", "customer-ai", "customer-ai.callback", 301)),
                CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
    }

    @Test
    void failsClosedWhenJwksKeyIsUnavailable() {
        CustomerAiCallbackJwtVerifier verifier = verifier(keyId -> {
            throw new CustomerAiCallbackAuthenticationException(
                    CustomerAiCallbackAuthenticationException.Reason.KEY_UNAVAILABLE);
        });

        assertReason(() -> verifier.verify("Bearer " + token(
                        "chapchap-customer-service", "customer-ai", "customer-ai.callback", 300)),
                CustomerAiCallbackAuthenticationException.Reason.KEY_UNAVAILABLE);
    }

    private CustomerAiCallbackJwtVerifier verifier(CustomerAiCallbackVerificationKeyResolver resolver) {
        return new CustomerAiCallbackJwtVerifier(
                resolver,
                new ObjectMapper(),
                "chapchap-auth-service",
                "chapchap-customer-service",
                "customer-ai",
                "customer-ai.callback",
                Duration.ofSeconds(300),
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    private String token(String audience, String subject, String scope, long lifetimeSeconds) {
        return Jwts.builder()
                .header().keyId("auth-1").and()
                .issuer("chapchap-auth-service")
                .audience().add(audience).and()
                .subject(subject)
                .claim("scope", scope)
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(lifetimeSeconds)))
                .id(UUID.randomUUID().toString())
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private void assertReason(
            Runnable action,
            CustomerAiCallbackAuthenticationException.Reason reason
    ) {
        assertThatThrownBy(action::run)
                .isExactlyInstanceOf(CustomerAiCallbackAuthenticationException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((CustomerAiCallbackAuthenticationException) error).reason()).isEqualTo(reason));
    }
}
