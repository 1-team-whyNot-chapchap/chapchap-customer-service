package com.chapchap.customer.global.security.customerai;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class AuthServiceCustomerAiServiceTokenProvider implements CustomerAiServiceTokenProvider {
    private static final long MAX_TOKEN_LIFETIME_SECONDS = 300;
    private static final long REFRESH_SKEW_SECONDS = 5;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestClient restClient;
    private final String tokenPath;
    private final String clientId;
    private final String clientSecret;
    private final String audience;
    private final String scope;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public AuthServiceCustomerAiServiceTokenProvider(
            RestClient restClient,
            String tokenPath,
            String clientId,
            String clientSecret,
            String audience,
            String scope
    ) {
        this(restClient, tokenPath, clientId, clientSecret, audience, scope, Clock.system(KST));
    }

    AuthServiceCustomerAiServiceTokenProvider(
            RestClient restClient,
            String tokenPath,
            String clientId,
            String clientSecret,
            String audience,
            String scope,
            Clock clock
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.tokenPath = requirePath(tokenPath);
        this.clientId = requireText(clientId, "clientId");
        this.clientSecret = requireText(clientSecret, "clientSecret");
        this.audience = requireText(audience, "audience");
        this.scope = requireText(scope, "scope");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.").withZone(KST);
    }

    @Override
    public String getServiceToken() {
        Instant now = clock.instant();
        CachedToken current = cachedToken;
        if (current != null && current.isUsableAt(now)) {
            return current.value();
        }

        synchronized (this) {
            now = clock.instant();
            current = cachedToken;
            if (current != null && current.isUsableAt(now)) {
                return current.value();
            }
            return requestAndCacheToken(now);
        }
    }

    private String requestAndCacheToken(Instant issuedAt) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("audience", audience);
        form.add("scope", scope);

        try {
            AuthServiceTokenResponse response = restClient.post()
                    .uri(tokenPath)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AuthServiceTokenResponse.class);
            if (!isValid(response)) {
                throw new CustomerAiAuthenticationUnavailableException();
            }
            long cacheSeconds = Math.max(1, response.expiresIn() - REFRESH_SKEW_SECONDS);
            cachedToken = new CachedToken(response.accessToken(), issuedAt.plusSeconds(cacheSeconds));
            return response.accessToken();
        } catch (CustomerAiAuthenticationUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CustomerAiAuthenticationUnavailableException();
        }
    }

    private boolean isValid(AuthServiceTokenResponse response) {
        return response != null
                && "Bearer".equals(response.tokenType())
                && response.expiresIn() > 0
                && response.expiresIn() <= MAX_TOKEN_LIFETIME_SECONDS
                && scope.equals(response.scope())
                && isCompactJwt(response.accessToken());
    }

    private boolean isCompactJwt(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        String[] segments = value.split("\\.", -1);
        return segments.length == 3
                && !segments[0].isEmpty()
                && !segments[1].isEmpty()
                && !segments[2].isEmpty();
    }

    private static String requirePath(String value) {
        String path = requireText(value, "tokenPath");
        if (!path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException("tokenPath must be an absolute path.");
        }
        return path;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must not be blank or padded.");
        }
        return value;
    }

    record AuthServiceTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn,
            String scope
    ) {
    }

    private record CachedToken(String value, Instant refreshAt) {
        boolean isUsableAt(Instant now) {
            return now.isBefore(refreshAt);
        }
    }
}
