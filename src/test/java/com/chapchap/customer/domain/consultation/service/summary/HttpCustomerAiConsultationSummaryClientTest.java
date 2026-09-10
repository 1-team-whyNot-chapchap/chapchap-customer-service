package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.dto.summary.CustomerAiConsultationSummaryCommand;
import com.chapchap.customer.global.exception.consultation.summary.CustomerAiConsultationSummaryClientException;
import com.chapchap.customer.domain.consultation.response.summary.CustomerAiConsultationSummaryAccepted;

import com.chapchap.customer.domain.consultation.constant.ConsultationStatus;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiServiceTokenProvider;
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

class HttpCustomerAiConsultationSummaryClientTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void submitsClosedConsultationMessagesAndAccepts202Contract() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiConsultationSummaryClient client = client(builder, () -> "service.header.signature");
        server.expect(requestTo("https://customer-ai.internal/internal/v1/consultation-summaries"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service.header.signature"))
                .andExpect(header("X-Request-Id", REQUEST_ID.toString()))
                .andExpect(header("Idempotency-Key", "consultation-summary:501"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "schemaVersion":"1.0",
                          "summaryJobId":7001,
                          "consultationId":501,
                          "messages":[
                            {"senderType":"USER","content":"환불 문의"},
                            {"senderType":"AI","content":"정책 안내"}
                          ],
                          "callback":{"resultUri":"/internal/v1/consultation-summary-results"}
                        }
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(acceptedJson()));

        assertThat(client.submit(command()))
                .isEqualTo(new CustomerAiConsultationSummaryAccepted(7001L, 501L));
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("failureStatuses")
    void mapsHttpFailureWithoutLeakingResponse(
            HttpStatus status,
            CustomerAiConsultationSummaryClientException.Reason expectedReason,
            boolean retryable
    ) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiConsultationSummaryClient client = client(builder, () -> "service.header.signature");
        server.expect(requestTo("https://customer-ai.internal/internal/v1/consultation-summaries"))
                .andRespond(withStatus(status).body("sensitive upstream response"));

        assertThatThrownBy(() -> client.submit(command()))
                .isExactlyInstanceOf(CustomerAiConsultationSummaryClientException.class)
                .satisfies(error -> {
                    CustomerAiConsultationSummaryClientException exception =
                            (CustomerAiConsultationSummaryClientException) error;
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
        HttpCustomerAiConsultationSummaryClient client = client(builder, () -> "service.header.signature");
        server.expect(requestTo("https://customer-ai.internal/internal/v1/consultation-summaries"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(acceptedJson()));

        assertThatThrownBy(() -> client.submit(command()))
                .isExactlyInstanceOf(CustomerAiConsultationSummaryClientException.class)
                .satisfies(error -> assertThat(((CustomerAiConsultationSummaryClientException) error).reason())
                        .isEqualTo(CustomerAiConsultationSummaryClientException.Reason.CONTRACT_ERROR));
        server.verify();
    }

    @Test
    void mapsSocketTimeoutAndRedactsCause() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpCustomerAiConsultationSummaryClient client = client(builder, () -> "service.header.signature");
        server.expect(requestTo("https://customer-ai.internal/internal/v1/consultation-summaries"))
                .andRespond(request -> {
                    throw new ResourceAccessException("private timeout", new SocketTimeoutException());
                });

        assertThatThrownBy(() -> client.submit(command()))
                .isExactlyInstanceOf(CustomerAiConsultationSummaryClientException.class)
                .satisfies(error -> assertThat(((CustomerAiConsultationSummaryClientException) error).reason())
                        .isEqualTo(CustomerAiConsultationSummaryClientException.Reason.TIMEOUT))
                .hasMessageNotContaining("private timeout")
                .hasNoCause();
        server.verify();
    }

    @Test
    void rejectsUnavailableServiceTokenBeforeHttpRequest() {
        HttpCustomerAiConsultationSummaryClient client = client(RestClient.builder(), () -> "not-a-jwt");

        assertThatThrownBy(() -> client.submit(command()))
                .isExactlyInstanceOf(CustomerAiConsultationSummaryClientException.class)
                .satisfies(error -> assertThat(((CustomerAiConsultationSummaryClientException) error).reason())
                        .isEqualTo(CustomerAiConsultationSummaryClientException.Reason.AUTHENTICATION_UNAVAILABLE))
                .hasNoCause();
    }

    private HttpCustomerAiConsultationSummaryClient client(
            RestClient.Builder builder,
            CustomerAiServiceTokenProvider tokenProvider
    ) {
        return new HttpCustomerAiConsultationSummaryClient(
                builder.build(),
                tokenProvider,
                new CustomerAiConsultationSummaryResponseParser(new ObjectMapper())
        );
    }

    private CustomerAiConsultationSummaryCommand command() {
        return new CustomerAiConsultationSummaryCommand(
                REQUEST_ID,
                7001L,
                501L,
                ConsultationStatus.CLOSED,
                List.of(
                        new CustomerAiConsultationSummaryCommand.Message(
                                CustomerAiConsultationSummaryCommand.SenderType.USER, "환불 문의"),
                        new CustomerAiConsultationSummaryCommand.Message(
                                CustomerAiConsultationSummaryCommand.SenderType.AI, "정책 안내")
                )
        );
    }

    private static Stream<Arguments> failureStatuses() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST,
                        CustomerAiConsultationSummaryClientException.Reason.REQUEST_REJECTED, false),
                Arguments.of(HttpStatus.UNAUTHORIZED,
                        CustomerAiConsultationSummaryClientException.Reason.AUTHENTICATION_REJECTED, false),
                Arguments.of(HttpStatus.FORBIDDEN,
                        CustomerAiConsultationSummaryClientException.Reason.AUTHENTICATION_REJECTED, false),
                Arguments.of(HttpStatus.CONFLICT,
                        CustomerAiConsultationSummaryClientException.Reason.IDEMPOTENCY_CONFLICT, false),
                Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT,
                        CustomerAiConsultationSummaryClientException.Reason.REQUEST_REJECTED, false),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE,
                        CustomerAiConsultationSummaryClientException.Reason.DEPENDENCY_UNAVAILABLE, true),
                Arguments.of(HttpStatus.GATEWAY_TIMEOUT,
                        CustomerAiConsultationSummaryClientException.Reason.TIMEOUT, true)
        );
    }

    private static String acceptedJson() {
        return """
                {"schemaVersion":"1.0","summaryJobId":7001,"consultationId":501,"status":"ACCEPTED"}
                """;
    }
}