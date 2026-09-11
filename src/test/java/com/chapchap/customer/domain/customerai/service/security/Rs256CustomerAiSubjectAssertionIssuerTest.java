package com.chapchap.customer.domain.customerai.service.security;

import com.chapchap.customer.domain.customerai.constant.security.CustomerAiSubjectScope;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;

import com.chapchap.customer.global.security.constant.RolePolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Rs256CustomerAiSubjectAssertionIssuerTest {
    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TOKEN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static KeyPair keyPair;

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void issuesCustomerAiCompatibleRs256SubjectAssertion() {
        Rs256CustomerAiSubjectAssertionIssuer issuer = new Rs256CustomerAiSubjectAssertionIssuer(
                keyPair.getPrivate(),
                "customer-subject-1",
                Duration.ofSeconds(60),
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")),
                () -> TOKEN_ID
        );
        CustomerAiSubjectAssertionRequest request = new CustomerAiSubjectAssertionRequest(
                42L,
                RolePolicy.CUSTOMER,
                List.of(
                        CustomerAiSubjectScope.POLICY_READ.value(),
                        CustomerAiSubjectScope.REFUND_READ.value()
                ),
                REQUEST_ID,
                501L
        );

        String token = issuer.issue(request);
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token);
        Claims claims = parsed.getPayload();

        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyId()).isEqualTo("customer-subject-1");
        assertThat(claims.getIssuer()).isEqualTo("chapchap-customer-service");
        assertThat(claims.getAudience()).containsExactly("chapchap-customer-ai");
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(42L);
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        Object scopes = claims.get("allowedAiScopes");
        assertThat(scopes).isInstanceOf(List.class);
        assertThat(scopes).isEqualTo(List.of("customer-ai.policy.read", "subscription.refund.read"));
        assertThat(claims.get("requestId", String.class)).isEqualTo(REQUEST_ID.toString());
        assertThat(((Number) claims.get("consultationId")).longValue()).isEqualTo(501L);
        assertThat(claims.get("kid", String.class)).isEqualTo("customer-subject-1");
        assertThat(claims.getId()).isEqualTo(TOKEN_ID.toString());
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(NOW.plusSeconds(60)));
    }

    @Test
    void normalizesIssuerClockZoneToKst() {
        Rs256CustomerAiSubjectAssertionIssuer issuer = new Rs256CustomerAiSubjectAssertionIssuer(
                keyPair.getPrivate(),
                "customer-subject-1",
                Duration.ofSeconds(60),
                Clock.fixed(NOW, ZoneId.of("UTC"))
        );

        assertThat(issuer.clockZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
    @Test
    void producesAValidSignatureThatRejectsTampering() {
        Rs256CustomerAiSubjectAssertionIssuer issuer = issuer(Duration.ofSeconds(30));
        String token = issuer.issue(validRequest());
        String[] segments = token.split("\\.");
        char replacement = segments[2].charAt(0) == 'a' ? 'b' : 'a';
        segments[2] = replacement + segments[2].substring(1);
        String tampered = String.join(".", segments);

        assertThatThrownBy(() -> Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsWeakKeysInvalidLifetimeAndBlankKeyId() throws Exception {
        KeyPairGenerator weakGenerator = KeyPairGenerator.getInstance("RSA");
        weakGenerator.initialize(1024);
        KeyPair weakKeyPair = weakGenerator.generateKeyPair();
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

        assertThatThrownBy(() -> new Rs256CustomerAiSubjectAssertionIssuer(
                weakKeyPair.getPrivate(), "key-1", Duration.ofSeconds(60), clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2048");
        assertThatThrownBy(() -> new Rs256CustomerAiSubjectAssertionIssuer(
                keyPair.getPrivate(), "key-1", Duration.ofSeconds(61), clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 seconds");
        assertThatThrownBy(() -> new Rs256CustomerAiSubjectAssertionIssuer(
                keyPair.getPrivate(), " ", Duration.ofSeconds(60), clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("keyId must not be blank.");
    }

    @Test
    void validatesAndDefensivelyCopiesSubjectContext() {
        ArrayList<String> scopes = new ArrayList<>(List.of(CustomerAiSubjectScope.POLICY_READ.value()));
        CustomerAiSubjectAssertionRequest request = new CustomerAiSubjectAssertionRequest(
                1L, RolePolicy.ADMIN, scopes, REQUEST_ID, 2L);
        scopes.add(CustomerAiSubjectScope.DELIVERY_READ.value());

        assertThat(request.allowedAiScopes()).containsExactly(CustomerAiSubjectScope.POLICY_READ.value());
        assertThatThrownBy(() -> new CustomerAiSubjectAssertionRequest(
                0L, RolePolicy.CUSTOMER, List.of(CustomerAiSubjectScope.POLICY_READ.value()), REQUEST_ID, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> new CustomerAiSubjectAssertionRequest(
                1L, RolePolicy.CUSTOMER, List.of(CustomerAiSubjectScope.POLICY_READ.value()), REQUEST_ID, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consultationId");
        assertThatThrownBy(() -> new CustomerAiSubjectAssertionRequest(
                1L, RolePolicy.CUSTOMER,
                List.of(CustomerAiSubjectScope.POLICY_READ.value(), CustomerAiSubjectScope.POLICY_READ.value()),
                REQUEST_ID, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
        assertThatThrownBy(() -> new CustomerAiSubjectAssertionRequest(
                1L, RolePolicy.CUSTOMER, List.of("unknown.scope"), REQUEST_ID, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unapproved");
    }

    private Rs256CustomerAiSubjectAssertionIssuer issuer(Duration lifetime) {
        return new Rs256CustomerAiSubjectAssertionIssuer(
                keyPair.getPrivate(),
                "customer-subject-1",
                lifetime,
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul"))
        );
    }

    private CustomerAiSubjectAssertionRequest validRequest() {
        return new CustomerAiSubjectAssertionRequest(
                42L,
                RolePolicy.CUSTOMER,
                List.of(CustomerAiSubjectScope.POLICY_READ.value()),
                REQUEST_ID,
                501L
        );
    }
}
