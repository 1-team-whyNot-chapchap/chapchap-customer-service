package com.chapchap.customer.domain.customerai.service.security;

import com.chapchap.customer.global.exception.customerai.security.CustomerAiAuthenticationUnavailableException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthServiceCustomerAiServiceTokenProviderTest {
    private static final String ENDPOINT = "https://auth.internal/internal/v1/service-tokens";

    @Test
    void reusesAClientCredentialsTokenUntilItsRefreshWindow() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=client_credentials"),
                        org.hamcrest.Matchers.containsString("client_id=customer-service"),
                        org.hamcrest.Matchers.containsString("client_secret=secret"),
                        org.hamcrest.Matchers.containsString("audience=chapchap-customer-ai"),
                        org.hamcrest.Matchers.containsString("scope=customer-ai.invoke"))))
                .andRespond(withSuccess("""
                        {
                          "access_token": "header.payload.signature",
                          "token_type": "Bearer",
                          "expires_in": 300,
                          "scope": "customer-ai.invoke"
                        }
                        """, MediaType.APPLICATION_JSON));
        AuthServiceCustomerAiServiceTokenProvider provider = provider(builder.build());

        assertThat(provider.getServiceToken()).isEqualTo("header.payload.signature");
        assertThat(provider.getServiceToken()).isEqualTo("header.payload.signature");
        server.verify();
    }

    @Test
    void requestsANewTokenAfterItsRefreshWindow() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(validResponse("first.token.value", 10),
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(validResponse("second.token.value", 10),
                MediaType.APPLICATION_JSON));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-07T00:00:00Z"));
        AuthServiceCustomerAiServiceTokenProvider provider = provider(builder.build(), clock);

        assertThat(provider.getServiceToken()).isEqualTo("first.token.value");
        clock.advance(Duration.ofSeconds(5));
        assertThat(provider.getServiceToken()).isEqualTo("second.token.value");
        server.verify();
    }

    @Test
    void rejectsInvalidResponseContractsWithoutExposingCredentials() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("""
                {
                  "access_token": "not-a-jwt",
                  "token_type": "bearer",
                  "expires_in": 301,
                  "scope": "other.scope"
                }
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider(builder.build()).getServiceToken())
                .isExactlyInstanceOf(CustomerAiAuthenticationUnavailableException.class)
                .hasMessage("Customer-AI internal authentication is unavailable.")
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining("not-a-jwt");
        server.verify();
    }

    @Test
    void convertsAuthServiceFailureToSafeFailClosedError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        assertThatThrownBy(() -> provider(builder.build()).getServiceToken())
                .isExactlyInstanceOf(CustomerAiAuthenticationUnavailableException.class)
                .hasNoCause()
                .hasMessageNotContaining("secret");
        server.verify();
    }

    private AuthServiceCustomerAiServiceTokenProvider provider(RestClient restClient) {
        return provider(restClient, Clock.systemUTC());
    }

    private AuthServiceCustomerAiServiceTokenProvider provider(RestClient restClient, Clock clock) {
        return new AuthServiceCustomerAiServiceTokenProvider(
                restClient,
                "/internal/v1/service-tokens",
                "customer-service",
                "secret",
                "chapchap-customer-ai",
                "customer-ai.invoke",
                clock);
    }

    private String validResponse(String token, long expiresIn) {
        return """
                {
                  "access_token": "%s",
                  "token_type": "Bearer",
                  "expires_in": %d,
                  "scope": "customer-ai.invoke"
                }
                """.formatted(token, expiresIn);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
