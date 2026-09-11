package com.chapchap.customer.domain.customerai.service.security;

import com.chapchap.customer.global.exception.customerai.security.CustomerAiAuthenticationUnavailableException;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;

import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class Rs256CustomerAiSubjectAssertionIssuer implements CustomerAiSubjectAssertionIssuer {
    static final String ISSUER = "chapchap-customer-service";
    static final String AUDIENCE = "chapchap-customer-ai";
    static final Duration MAX_LIFETIME = Duration.ofSeconds(60);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MINIMUM_RSA_BITS = 2048;

    private final PrivateKey privateKey;
    private final String keyId;
    private final Duration lifetime;
    private final Clock clock;
    private final Supplier<UUID> tokenIdSupplier;

    public Rs256CustomerAiSubjectAssertionIssuer(
            PrivateKey privateKey,
            String keyId,
            Duration lifetime,
            Clock clock
    ) {
        this(privateKey, keyId, lifetime, clock, UUID::randomUUID);
    }

    Rs256CustomerAiSubjectAssertionIssuer(
            PrivateKey privateKey,
            String keyId,
            Duration lifetime,
            Clock clock,
            Supplier<UUID> tokenIdSupplier
    ) {
        this.privateKey = requireStrongRsaKey(privateKey);
        this.keyId = requireText(keyId, "keyId");
        this.lifetime = requireLifetime(lifetime);
        this.clock = Objects.requireNonNull(clock, "clock must not be null.").withZone(KST);
        this.tokenIdSupplier = Objects.requireNonNull(tokenIdSupplier, "tokenIdSupplier must not be null.");
    }

    ZoneId clockZone() {
        return clock.getZone();
    }

    @Override
    public String issue(CustomerAiSubjectAssertionRequest request) {
        Objects.requireNonNull(request, "request must not be null.");
        Instant issuedAt = clock.instant();
        UUID tokenId = Objects.requireNonNull(tokenIdSupplier.get(), "tokenId must not be null.");

        try {
            return Jwts.builder()
                    .header()
                    .keyId(keyId)
                    .and()
                    .issuer(ISSUER)
                    .audience()
                    .add(AUDIENCE)
                    .and()
                    .issuedAt(Date.from(issuedAt))
                    .expiration(Date.from(issuedAt.plus(lifetime)))
                    .id(tokenId.toString())
                    .claim("kid", keyId)
                    .claim("userId", request.userId())
                    .claim("role", request.role().getRole())
                    .claim("allowedAiScopes", request.allowedAiScopes())
                    .claim("requestId", request.requestId().toString())
                    .claim("consultationId", request.consultationId())
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();
        } catch (RuntimeException exception) {
            throw new CustomerAiAuthenticationUnavailableException();
        }
    }

    private static PrivateKey requireStrongRsaKey(PrivateKey privateKey) {
        Objects.requireNonNull(privateKey, "privateKey must not be null.");
        if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)
                || rsaPrivateKey.getModulus().bitLength() < MINIMUM_RSA_BITS) {
            throw new IllegalArgumentException("A RSA private key of at least 2048 bits is required.");
        }
        return privateKey;
    }

    private static Duration requireLifetime(Duration lifetime) {
        Objects.requireNonNull(lifetime, "lifetime must not be null.");
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalArgumentException("Subject assertion lifetime must be between 1 and 60 seconds.");
        }
        return lifetime;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value;
    }
}
