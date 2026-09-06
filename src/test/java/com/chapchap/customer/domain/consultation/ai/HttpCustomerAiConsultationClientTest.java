package com.chapchap.customer.domain.consultation.ai;

import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.customerai.CustomerAiRequestCredentialsProvider;
import com.chapchap.customer.global.security.customerai.CustomerAiSubjectAssertionRequest;
import com.chapchap.customer.global.security.customerai.CustomerAiSubjectScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpCustomerAiConsultationClientTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void sendsAuthenticatedCandidateRequestAndParsesVerifiedResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiConsultationClient client = client(builder);
        server.expect(requestTo("https://customer-ai.internal/internal/v1/consultation-responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service.header.signature"))
                .andExpect(header("X-Subject-Assertion", "subject.header.signature"))
                .andExpect(header("X-Request-Id", REQUEST_ID.toString()))
                .andExpect(header("Idempotency-Key", "consultation-response:501:9002"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "schemaVersion":"1.0",
                          "requestId":"11111111-1111-4111-8111-111111111111",
                          "consultationId":501,
                          "triggerMessageId":9002,
                          "subject":{
                            "userId":42,
                            "role":"CUSTOMER",
                            "allowedAiScopes":["customer-ai.policy.read"]
                          },
                          "message":"환불 정책 알려줘",
                          "conversationContext":[]
                        }
                        """))
                .andRespond(withSuccess(answerJson(), MediaType.APPLICATION_JSON));

        CustomerAiConsultationResult result = client.respond(validCommand());

        assertThat(result.decision()).isEqualTo(CustomerAiConsultationResult.Decision.ANSWER);
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("failureStatuses")
    void mapsHttpFailuresWithoutExposingResponseBody(
            HttpStatus status,
            CustomerAiConsultationClientException.Reason reason,
            boolean retryable
    ) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiConsultationClient client = client(builder);
        server.expect(requestTo("https://customer-ai.internal/internal/v1/consultation-responses"))
                .andRespond(withStatus(status).body("sensitive upstream body"));

        assertThatThrownBy(() -> client.respond(validCommand()))
                .isExactlyInstanceOf(CustomerAiConsultationClientException.class)
                .satisfies(error -> {
                    CustomerAiConsultationClientException exception =
                            (CustomerAiConsultationClientException) error;
                    assertThat(exception.reason()).isEqualTo(reason);
                    assertThat(exception.retryable()).isEqualTo(retryable);
                })
                .hasMessageNotContaining("sensitive upstream body")
                .hasNoCause();
        server.verify();
    }

    @Test
    void mapsClientSocketTimeoutWithoutExposingItsCause() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiConsultationClient client = client(builder);
        server.expect(requestTo("https://customer-ai.internal/internal/v1/consultation-responses"))
                .andRespond(request -> {
                    throw new ResourceAccessException("sensitive timeout detail", new SocketTimeoutException());
                });

        assertThatThrownBy(() -> client.respond(validCommand()))
                .isExactlyInstanceOf(CustomerAiConsultationClientException.class)
                .satisfies(error -> {
                    CustomerAiConsultationClientException exception =
                            (CustomerAiConsultationClientException) error;
                    assertThat(exception.reason())
                            .isEqualTo(CustomerAiConsultationClientException.Reason.TIMEOUT);
                    assertThat(exception.retryable()).isTrue();
                })
                .hasMessageNotContaining("sensitive timeout detail")
                .hasNoCause();
        server.verify();
    }
    @Test
    void rejectsMalformedSuccessfulResponseAsContractError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiConsultationClient client = client(builder);
        server.expect(requestTo("https://customer-ai.internal/internal/v1/consultation-responses"))
                .andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.respond(validCommand()))
                .isExactlyInstanceOf(CustomerAiConsultationClientException.class)
                .satisfies(error -> assertThat(((CustomerAiConsultationClientException) error).reason())
                        .isEqualTo(CustomerAiConsultationClientException.Reason.CONTRACT_ERROR));
        server.verify();
    }

    @Test
    void validatesCommandContextBeforeAnyHttpRequest() {
        CustomerAiSubjectAssertionRequest subject = subject(RolePolicy.ADMIN);

        assertThatThrownBy(() -> new CustomerAiConsultationCommand(
                REQUEST_ID, 501L, 9002L, subject, "문의", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roles");

        ArrayList<String> context = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            context.add("message-" + index);
        }
        assertThatThrownBy(() -> new CustomerAiConsultationCommand(
                REQUEST_ID, 501L, 9002L, subject(RolePolicy.CUSTOMER), "문의", context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationContext");
    }

    private HttpCustomerAiConsultationClient client(RestClient.Builder builder) {
        CustomerAiRequestCredentialsProvider credentials = new CustomerAiRequestCredentialsProvider(
                () -> "service.header.signature",
                request -> "subject.header.signature"
        );
        return new HttpCustomerAiConsultationClient(
                builder.build(),
                credentials,
                new CustomerAiConsultationResponseParser(new ObjectMapper())
        );
    }

    private CustomerAiConsultationCommand validCommand() {
        return new CustomerAiConsultationCommand(
                REQUEST_ID,
                501L,
                9002L,
                subject(RolePolicy.CUSTOMER),
                "환불 정책 알려줘",
                List.of()
        );
    }

    private CustomerAiSubjectAssertionRequest subject(RolePolicy role) {
        return new CustomerAiSubjectAssertionRequest(
                42L,
                role,
                List.of(CustomerAiSubjectScope.POLICY_READ.value()),
                REQUEST_ID,
                501L
        );
    }

    private static Stream<Arguments> failureStatuses() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST,
                        CustomerAiConsultationClientException.Reason.REQUEST_REJECTED, false),
                Arguments.of(HttpStatus.UNAUTHORIZED,
                        CustomerAiConsultationClientException.Reason.AUTHENTICATION_REJECTED, false),
                Arguments.of(HttpStatus.FORBIDDEN,
                        CustomerAiConsultationClientException.Reason.AUTHENTICATION_REJECTED, false),
                Arguments.of(HttpStatus.CONFLICT,
                        CustomerAiConsultationClientException.Reason.IDEMPOTENCY_CONFLICT, false),
                Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT,
                        CustomerAiConsultationClientException.Reason.UNSAFE_RESPONSE, false),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE,
                        CustomerAiConsultationClientException.Reason.DEPENDENCY_UNAVAILABLE, true),
                Arguments.of(HttpStatus.GATEWAY_TIMEOUT,
                        CustomerAiConsultationClientException.Reason.TIMEOUT, true)
        );
    }

    private String answerJson() {
        return """
                {"schemaVersion":"1.0","requestId":"11111111-1111-4111-8111-111111111111",
                "decision":"ANSWER","answer":"환불 정책의 적용 조건을 안내합니다.","route":"POLICY",
                "degraded":false,"handoffRequired":false,
                "evidence":[{"knowledgeVersionId":101,"chunkId":"refund-policy-v1-0001","retrievalRank":1,"retrievalScore":0.91}]}
                """;
    }
}
