package com.chapchap.customer.global.security.customerai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthServiceCustomerAiServiceTokenProviderTest {
    private static final String ENDPOINT = "https://auth.internal/internal/v1/service-tokens";

    @Test
    void requestsANewClientCredentialsTokenForEveryInvocation() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://auth.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(twice(), requestTo(ENDPOINT))
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
        return new AuthServiceCustomerAiServiceTokenProvider(
                restClient,
                "/internal/v1/service-tokens",
                "customer-service",
                "secret",
                "chapchap-customer-ai",
                "customer-ai.invoke");
    }
}
