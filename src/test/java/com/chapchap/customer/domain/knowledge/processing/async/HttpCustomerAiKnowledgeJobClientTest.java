package com.chapchap.customer.domain.knowledge.processing.async;

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
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpCustomerAiKnowledgeJobClientTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void submitsAuthenticatedCandidateJobAndAcceptsOnly202Contract() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiKnowledgeJobClient client = client(builder, () -> "service.header.signature");
        server.expect(requestTo("https://customer-ai.internal/internal/v1/knowledge-processings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service.header.signature"))
                .andExpect(header("X-Request-Id", REQUEST_ID.toString()))
                .andExpect(header("Idempotency-Key", "101:HYBRID_POLICY_V1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "schemaVersion":"1.0",
                          "knowledgeVersionId":101,
                          "attempt":1,
                          "source":{
                            "downloadUrl":"https://private-minio.example/knowledge.txt?signature=secret",
                            "contentType":"text/plain",
                            "fileSize":4
                          },
                          "metadata":{
                            "documentKey":"refund-policy",
                            "sourceService":"subscription-service",
                            "category":"REFUND",
                            "version":"2026.09",
                            "effectiveFrom":"2026-09-01T00:00+09:00"
                          },
                          "chunkProfile":"HYBRID_POLICY_V1",
                          "callback":{"resultUri":"/internal/v1/knowledge-processing-results"}
                        }
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(acceptedJson()));

        CustomerAiKnowledgeJobAccepted accepted = client.submit(command());

        assertThat(accepted).isEqualTo(new CustomerAiKnowledgeJobAccepted(8001L, 101L));
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("failureStatuses")
    void mapsHttpFailureWithoutLeakingResponse(
            HttpStatus status,
            CustomerAiKnowledgeJobClientException.Reason expectedReason,
            boolean retryable
    ) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiKnowledgeJobClient client = client(builder, () -> "service.header.signature");
        server.expect(requestTo("https://customer-ai.internal/internal/v1/knowledge-processings"))
                .andRespond(withStatus(status).body("sensitive upstream response"));

        assertThatThrownBy(() -> client.submit(command()))
                .isExactlyInstanceOf(CustomerAiKnowledgeJobClientException.class)
                .satisfies(error -> {
                    CustomerAiKnowledgeJobClientException exception =
                            (CustomerAiKnowledgeJobClientException) error;
                    assertThat(exception.reason()).isEqualTo(expectedReason);
                    assertThat(exception.retryable()).isEqualTo(retryable);
                })
                .hasMessageNotContaining("sensitive upstream response")
                .hasNoCause();
        server.verify();
    }

    @Test
    void rejectsSuccessfulStatusOtherThan202() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiKnowledgeJobClient client = client(builder, () -> "service.header.signature");
        server.expect(requestTo("https://customer-ai.internal/internal/v1/knowledge-processings"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(acceptedJson()));

        assertThatThrownBy(() -> client.submit(command()))
                .isExactlyInstanceOf(CustomerAiKnowledgeJobClientException.class)
                .satisfies(error -> assertThat(((CustomerAiKnowledgeJobClientException) error).reason())
                        .isEqualTo(CustomerAiKnowledgeJobClientException.Reason.CONTRACT_ERROR));
        server.verify();
    }

    @Test
    void mapsSocketTimeoutAndRedactsCause() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiKnowledgeJobClient client = client(builder, () -> "service.header.signature");
        server.expect(requestTo("https://customer-ai.internal/internal/v1/knowledge-processings"))
                .andRespond(request -> {
                    throw new ResourceAccessException("private timeout", new SocketTimeoutException());
                });

        assertThatThrownBy(() -> client.submit(command()))
                .isExactlyInstanceOf(CustomerAiKnowledgeJobClientException.class)
                .satisfies(error -> assertThat(((CustomerAiKnowledgeJobClientException) error).reason())
                        .isEqualTo(CustomerAiKnowledgeJobClientException.Reason.TIMEOUT))
                .hasMessageNotContaining("private timeout")
                .hasNoCause();
        server.verify();
    }

    @Test
    void rejectsUnavailableServiceTokenBeforeHttpRequest() {
        HttpCustomerAiKnowledgeJobClient client = client(RestClient.builder(), () -> "not-a-jwt");

        assertThatThrownBy(() -> client.submit(command()))
                .isExactlyInstanceOf(CustomerAiKnowledgeJobClientException.class)
                .satisfies(error -> assertThat(((CustomerAiKnowledgeJobClientException) error).reason())
                        .isEqualTo(CustomerAiKnowledgeJobClientException.Reason.AUTHENTICATION_UNAVAILABLE))
                .hasNoCause();
    }

    private HttpCustomerAiKnowledgeJobClient client(
            RestClient.Builder builder,
            com.chapchap.customer.global.security.customerai.CustomerAiServiceTokenProvider tokenProvider
    ) {
        return new HttpCustomerAiKnowledgeJobClient(
                builder.build(),
                tokenProvider,
                new CustomerAiKnowledgeJobResponseParser(new ObjectMapper())
        );
    }

    private CustomerAiKnowledgeJobCommand command() {
        return new CustomerAiKnowledgeJobCommand(
                REQUEST_ID,
                101L,
                1,
                new CustomerAiKnowledgeJobCommand.Source(
                        URI.create("https://private-minio.example/knowledge.txt?signature=secret"),
                        "text/plain",
                        4L
                ),
                new CustomerAiKnowledgeJobCommand.Metadata(
                        "refund-policy",
                        "subscription-service",
                        "REFUND",
                        "2026.09",
                        OffsetDateTime.parse("2026-09-01T00:00:00+09:00")
                ),
                "HYBRID_POLICY_V1"
        );
    }

    private static Stream<Arguments> failureStatuses() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST,
                        CustomerAiKnowledgeJobClientException.Reason.REQUEST_REJECTED, false),
                Arguments.of(HttpStatus.UNAUTHORIZED,
                        CustomerAiKnowledgeJobClientException.Reason.AUTHENTICATION_REJECTED, false),
                Arguments.of(HttpStatus.FORBIDDEN,
                        CustomerAiKnowledgeJobClientException.Reason.AUTHENTICATION_REJECTED, false),
                Arguments.of(HttpStatus.CONFLICT,
                        CustomerAiKnowledgeJobClientException.Reason.IDEMPOTENCY_CONFLICT, false),
                Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT,
                        CustomerAiKnowledgeJobClientException.Reason.REQUEST_REJECTED, false),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE,
                        CustomerAiKnowledgeJobClientException.Reason.DEPENDENCY_UNAVAILABLE, true),
                Arguments.of(HttpStatus.GATEWAY_TIMEOUT,
                        CustomerAiKnowledgeJobClientException.Reason.TIMEOUT, true)
        );
    }

    private static String acceptedJson() {
        return """
                {"schemaVersion":"1.0","processingId":8001,"knowledgeVersionId":101,"status":"ACCEPTED"}
                """;
    }
}