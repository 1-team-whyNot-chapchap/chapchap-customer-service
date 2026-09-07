package com.chapchap.customer.global.security.customerai;

import com.chapchap.customer.global.error.custom.customerai.CustomerAiCallbackAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;

public final class CustomerAiCallbackJwtVerifier {
    private final CustomerAiCallbackVerificationKeyResolver keyResolver;
    private final ObjectMapper objectMapper;
    private final String issuer;
    private final String audience;
    private final String subject;
    private final String requiredScope;
    private final Duration maximumLifetime;
    private final Clock clock;

    public CustomerAiCallbackJwtVerifier(
            CustomerAiCallbackVerificationKeyResolver keyResolver,
            ObjectMapper objectMapper,
            String issuer,
            String audience,
            String subject,
            String requiredScope,
            Duration maximumLifetime,
            Clock clock
    ) {
        this.keyResolver = Objects.requireNonNull(keyResolver);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.subject = requireText(subject, "subject");
        this.requiredScope = requireText(requiredScope, "scope");
        this.maximumLifetime = Objects.requireNonNull(maximumLifetime);
        this.clock = Objects.requireNonNull(clock);
        if (maximumLifetime.isZero() || maximumLifetime.isNegative()) {
            throw new IllegalArgumentException("Maximum token lifetime must be positive.");
        }
    }

    public void verify(String authorization) {
        String token = bearerToken(authorization);
        try {
            JsonNode header = decodeHeader(token);
            if (!"RS256".equals(text(header, "alg"))) {
                throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
            }
            String keyId = text(header, "kid");
            PublicKey publicKey = keyResolver.resolve(keyId);
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .clock(() -> Date.from(clock.instant()))
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            validateClaims(claims);
        } catch (CustomerAiCallbackAuthenticationException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
        }
    }

    private void validateClaims(Claims claims) {
        if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
        }
        if (!subject.equals(claims.getSubject())) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.FORBIDDEN_SERVICE);
        }
        String scope = claims.get("scope", String.class);
        if (scope == null || Arrays.stream(scope.trim().split("\\s+"))
                .noneMatch(requiredScope::equals)) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.FORBIDDEN_SERVICE);
        }
        String jwtId = claims.getId();
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        Instant now = clock.instant();
        if (jwtId == null || jwtId.isBlank() || issuedAt == null || expiration == null
                || issuedAt.toInstant().isAfter(now)
                || !expiration.toInstant().isAfter(now)
                || Duration.between(issuedAt.toInstant(), expiration.toInstant()).compareTo(maximumLifetime) > 0) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
        }
    }

    private JsonNode decodeHeader(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                throw new IllegalArgumentException("Invalid compact JWT.");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0].getBytes(StandardCharsets.US_ASCII));
            return objectMapper.readTree(decoded);
        } catch (Exception exception) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
        }
        return value.asText();
    }

    private String bearerToken(String authorization) {
        if (authorization == null) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.MISSING_CREDENTIALS);
        }
        String[] parts = authorization.trim().split("\\s+");
        if (parts.length != 2 || !"Bearer".equalsIgnoreCase(parts[0]) || parts[1].isBlank()) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.MISSING_CREDENTIALS);
        }
        return parts[1];
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Callback JWT " + field + " must not be blank.");
        }
        return value;
    }

    private CustomerAiCallbackAuthenticationException authenticationError(
            CustomerAiCallbackAuthenticationException.Reason reason
    ) {
        return new CustomerAiCallbackAuthenticationException(reason);
    }
}
