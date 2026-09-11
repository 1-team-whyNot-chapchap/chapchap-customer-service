package com.chapchap.customer.domain.customerai.service.security;

import com.chapchap.customer.domain.customerai.constant.security.CustomerAiSubjectScope;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiRequestCredentials;
import com.chapchap.customer.global.exception.customerai.security.CustomerAiAuthenticationUnavailableException;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;

import com.chapchap.customer.global.security.constant.RolePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiRequestCredentialsProviderTest {

    @Test
    void combinesBearerServiceTokenAndSubjectAssertionWithoutExposingThem() {
        CustomerAiRequestCredentialsProvider provider = new CustomerAiRequestCredentialsProvider(
                () -> "service-header.service-payload.service-signature",
                request -> "subject-header.subject-payload.subject-signature"
        );

        CustomerAiRequestCredentials credentials = provider.create(validRequest());

        assertThat(credentials.authorization())
                .isEqualTo("Bearer service-header.service-payload.service-signature");
        assertThat(credentials.subjectAssertion())
                .isEqualTo("subject-header.subject-payload.subject-signature");
        assertThat(credentials.toString())
                .isEqualTo("CustomerAiRequestCredentials[authorization=[REDACTED], subjectAssertion=[REDACTED]]")
                .doesNotContain("service-header", "subject-header");
    }

    @Test
    void failsClosedBeforeIssuingAssertionWhenServiceTokenIsMissingOrMalformed() {
        AtomicInteger issueCount = new AtomicInteger();
        CustomerAiSubjectAssertionIssuer issuer = request -> {
            issueCount.incrementAndGet();
            return "subject.header.signature";
        };

        assertUnavailable(new CustomerAiRequestCredentialsProvider(() -> "", issuer));
        assertUnavailable(new CustomerAiRequestCredentialsProvider(() -> "not-a-jwt", issuer));
        assertUnavailable(new CustomerAiRequestCredentialsProvider(() -> "a.b.", issuer));

        assertThat(issueCount).hasValue(0);
    }

    @Test
    void hidesServiceProviderFailureDetails() {
        CustomerAiRequestCredentialsProvider provider = new CustomerAiRequestCredentialsProvider(
                () -> {
                    throw new IllegalStateException("secret provider detail");
                },
                request -> "subject.header.signature"
        );

        assertThatThrownBy(() -> provider.create(validRequest()))
                .isExactlyInstanceOf(CustomerAiAuthenticationUnavailableException.class)
                .hasMessage("Customer-AI internal authentication is unavailable.")
                .hasNoCause()
                .hasMessageNotContaining("secret provider detail");
    }

    @Test
    void rejectsMissingSubjectContextBeforeCallingServiceTokenProvider() {
        AtomicInteger tokenCallCount = new AtomicInteger();
        CustomerAiRequestCredentialsProvider provider = new CustomerAiRequestCredentialsProvider(
                () -> {
                    tokenCallCount.incrementAndGet();
                    return "service.header.signature";
                },
                request -> "subject.header.signature"
        );

        assertThatThrownBy(() -> provider.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null.");
        assertThat(tokenCallCount).hasValue(0);
    }
    @Test
    void rejectsMalformedAssertionResult() {
        CustomerAiRequestCredentialsProvider provider = new CustomerAiRequestCredentialsProvider(
                () -> "service.header.signature",
                request -> "malformed"
        );

        assertThatThrownBy(() -> provider.create(validRequest()))
                .isExactlyInstanceOf(CustomerAiAuthenticationUnavailableException.class)
                .hasMessage("Customer-AI internal authentication is unavailable.");
    }

    private void assertUnavailable(CustomerAiRequestCredentialsProvider provider) {
        assertThatThrownBy(() -> provider.create(validRequest()))
                .isExactlyInstanceOf(CustomerAiAuthenticationUnavailableException.class)
                .hasMessage("Customer-AI internal authentication is unavailable.");
    }

    private CustomerAiSubjectAssertionRequest validRequest() {
        return new CustomerAiSubjectAssertionRequest(
                42L,
                RolePolicy.CUSTOMER,
                List.of(CustomerAiSubjectScope.POLICY_READ.value()),
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                501L
        );
    }
}
