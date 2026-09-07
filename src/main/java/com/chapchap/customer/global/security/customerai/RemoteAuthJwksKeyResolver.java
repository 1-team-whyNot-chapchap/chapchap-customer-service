package com.chapchap.customer.global.security.customerai;

import com.chapchap.customer.global.error.custom.customerai.CustomerAiCallbackAuthenticationException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class RemoteAuthJwksKeyResolver implements CustomerAiCallbackVerificationKeyResolver {
    private static final int MAX_KEYS = 10;
    private static final int MAX_COMPONENT_LENGTH = 1_024;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Duration cacheDuration;
    private final Clock clock;
    private volatile Cache cache = new Cache(Map.of(), Instant.EPOCH);
    private volatile Instant lastMissingKeyRefreshAt = Instant.EPOCH;

    public RemoteAuthJwksKeyResolver(
            RestClient restClient,
            ObjectMapper objectMapper,
            Duration cacheDuration,
            Clock clock
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.cacheDuration = Objects.requireNonNull(cacheDuration);
        this.clock = Objects.requireNonNull(clock);
        if (cacheDuration.isNegative() || cacheDuration.isZero()) {
            throw new IllegalArgumentException("JWKS cache duration must be positive.");
        }
    }

    @Override
    public PublicKey resolve(String keyId) {
        if (keyId == null || keyId.isBlank() || keyId.length() > 128 || keyId.chars().anyMatch(Character::isWhitespace)) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
        }
        Cache current = cache;
        boolean refreshed = false;
        if (!clock.instant().isBefore(current.expiresAt())) {
            current = refresh(false);
            refreshed = true;
        }
        PublicKey key = current.keys().get(keyId);
        if (key == null && !refreshed) {
            current = refresh(true);
            key = current.keys().get(keyId);
        }
        if (key == null) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.INVALID_TOKEN);
        }
        return key;
    }

    private synchronized Cache refresh(boolean missingKey) {
        Cache current = cache;
        Instant now = clock.instant();
        if (!missingKey && now.isBefore(current.expiresAt())) {
            return current;
        }
        if (missingKey && now.isBefore(lastMissingKeyRefreshAt.plusSeconds(30))) {
            return current;
        }
        if (missingKey) {
            lastMissingKeyRefreshAt = now;
        }
        try {
            String body = restClient.get().retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode keysNode = root == null ? null : root.get("keys");
            if (keysNode == null || !keysNode.isArray() || keysNode.isEmpty() || keysNode.size() > MAX_KEYS) {
                throw new IllegalArgumentException("Invalid JWKS key set.");
            }
            Map<String, PublicKey> keys = new HashMap<>();
            for (JsonNode keyNode : keysNode) {
                String keyId = requiredText(keyNode, "kid", 128);
                if (!"RSA".equals(requiredText(keyNode, "kty", 10))
                        || !"RS256".equals(requiredText(keyNode, "alg", 10))
                        || !"sig".equals(requiredText(keyNode, "use", 10))) {
                    throw new IllegalArgumentException("Unsupported JWKS key.");
                }
                PublicKey previous = keys.put(keyId, rsaKey(
                        requiredText(keyNode, "n", MAX_COMPONENT_LENGTH),
                        requiredText(keyNode, "e", MAX_COMPONENT_LENGTH)));
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate JWKS key ID.");
                }
            }
            Cache refreshed = new Cache(Map.copyOf(keys), now.plus(cacheDuration));
            cache = refreshed;
            return refreshed;
        } catch (CustomerAiCallbackAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw authenticationError(CustomerAiCallbackAuthenticationException.Reason.KEY_UNAVAILABLE);
        }
    }

    private PublicKey rsaKey(String modulus, String exponent) throws Exception {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        BigInteger n = new BigInteger(1, decoder.decode(modulus));
        BigInteger e = new BigInteger(1, decoder.decode(exponent));
        if (n.bitLength() < 2_048 || e.signum() <= 0) {
            throw new IllegalArgumentException("Invalid RSA verification key.");
        }
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    private String requiredText(JsonNode node, String field, int maximumLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("Missing JWKS field.");
        }
        String text = value.asText();
        if (text.isBlank() || text.length() > maximumLength || text.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid JWKS field.");
        }
        return text;
    }

    private CustomerAiCallbackAuthenticationException authenticationError(
            CustomerAiCallbackAuthenticationException.Reason reason
    ) {
        return new CustomerAiCallbackAuthenticationException(reason);
    }

    private record Cache(Map<String, PublicKey> keys, Instant expiresAt) {
    }
}
