package com.chapchap.customer.global.observability.customerai;

import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticEventType;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationCommand;
import com.chapchap.customer.domain.consultation.service.ai.CustomerAiConsultationResponseParser;
import com.chapchap.customer.domain.consultation.service.ai.HttpCustomerAiConsultationClient;
import com.chapchap.customer.domain.consultation.constant.ConsultationStatus;
import com.chapchap.customer.domain.consultation.dto.summary.CustomerAiConsultationSummaryCommand;
import com.chapchap.customer.domain.consultation.service.summary.CustomerAiConsultationSummaryResponseParser;
import com.chapchap.customer.domain.consultation.service.summary.HttpCustomerAiConsultationSummaryClient;
import com.chapchap.customer.domain.knowledge.dto.processing.async.CustomerAiKnowledgeJobCommand;
import com.chapchap.customer.domain.knowledge.service.processing.async.CustomerAiKnowledgeJobResponseParser;
import com.chapchap.customer.domain.knowledge.service.processing.async.HttpCustomerAiKnowledgeJobClient;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.exception.customerai.security.CustomerAiAuthenticationUnavailableException;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiRequestCredentialsProvider;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CustomerAiClientDiagnosticsTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void correlatesConsultationKnowledgeAndSummarySuccesses() {
        List<CustomerAiDiagnosticEvent> events = new ArrayList<>();
        CustomerAiDiagnosticPublisher diagnostics = new CustomerAiDiagnosticPublisher(
                events::add, () -> "trace-clients");

        RestClient.Builder consultationBuilder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer consultationServer = MockRestServiceServer.bindTo(consultationBuilder).build();
        consultationServer.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withSuccess(consultationResponse(), MediaType.APPLICATION_JSON));
        consultationClient(consultationBuilder, diagnostics, "service.header.signature")
                .respond(consultationCommand());

        RestClient.Builder knowledgeBuilder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer knowledgeServer = MockRestServiceServer.bindTo(knowledgeBuilder).build();
        knowledgeServer.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"schemaVersion\":\"1.0\",\"processingId\":8001,\"knowledgeVersionId\":101,\"status\":\"ACCEPTED\"}"));
        new HttpCustomerAiKnowledgeJobClient(
                knowledgeBuilder.build(),
                () -> "service.header.signature",
                new CustomerAiKnowledgeJobResponseParser(OBJECT_MAPPER),
                diagnostics
        ).submit(knowledgeCommand());

        RestClient.Builder summaryBuilder = RestClient.builder().baseUrl("https://customer-ai.internal");
        MockRestServiceServer summaryServer = MockRestServiceServer.bindTo(summaryBuilder).build();
        summaryServer.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"schemaVersion\":\"1.0\",\"summaryJobId\":7001,\"consultationId\":501,\"status\":\"ACCEPTED\"}"));
        new HttpCustomerAiConsultationSummaryClient(
                summaryBuilder.build(),
                () -> "service.header.signature",
                new CustomerAiConsultationSummaryResponseParser(OBJECT_MAPPER),
                diagnostics
        ).submit(summaryCommand());

        assertThat(events).extracting(CustomerAiDiagnosticEvent::eventType)
                .containsExactly(
                        CustomerAiDiagnosticEventType.CONSULTATION_RESULT,
                        CustomerAiDiagnosticEventType.KNOWLEDGE_SUBMITTED,
                        CustomerAiDiagnosticEventType.SUMMARY_SUBMITTED
                );
        assertThat(events).allSatisfy(event -> {
            assertThat(event.requestId()).isEqualTo(REQUEST_ID);
            assertThat(event.traceId()).isEqualTo("trace-clients");
        });
        assertThat(events.get(1).processingId()).isEqualTo(8001);
        assertThat(events.get(2).processingId()).isNull();

        consultationServer.verify();
        knowledgeServer.verify();
        summaryServer.verify();
    }

    @Test
    void recordsAuthenticationFailuresWithoutCredentials() {
        List<CustomerAiDiagnosticEvent> events = new ArrayList<>();
        CustomerAiDiagnosticPublisher diagnostics = new CustomerAiDiagnosticPublisher(events::add, () -> null);

        assertThatThrownBy(() -> consultationClient(RestClient.builder(), diagnostics, "invalid-token")
                .respond(consultationCommand()))
                .isInstanceOf(CustomerAiAuthenticationUnavailableException.class);
        assertThatThrownBy(() -> new HttpCustomerAiKnowledgeJobClient(
                RestClient.builder().build(),
                () -> "invalid-token",
                new CustomerAiKnowledgeJobResponseParser(OBJECT_MAPPER),
                diagnostics
        ).submit(knowledgeCommand())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new HttpCustomerAiConsultationSummaryClient(
                RestClient.builder().build(),
                () -> "invalid-token",
                new CustomerAiConsultationSummaryResponseParser(OBJECT_MAPPER),
                diagnostics
        ).submit(summaryCommand())).isInstanceOf(RuntimeException.class);

        assertThat(events).hasSize(3).allSatisfy(event -> {
            assertThat(event.requestId()).isEqualTo(REQUEST_ID);
            assertThat(event.failureCode()).isEqualTo(CustomerAiDiagnosticFailureCode.AUTHENTICATION_UNAVAILABLE);
            assertThat(event.retryable()).isFalse();
        });
    }

    private HttpCustomerAiConsultationClient consultationClient(
            RestClient.Builder builder,
            CustomerAiDiagnosticPublisher diagnostics,
            String serviceToken
    ) {
        return new HttpCustomerAiConsultationClient(
                builder.build(),
                new CustomerAiRequestCredentialsProvider(
                        () -> serviceToken,
                        request -> "subject.header.signature"
                ),
                new CustomerAiConsultationResponseParser(OBJECT_MAPPER),
                diagnostics
        );
    }

    private CustomerAiConsultationCommand consultationCommand() {
        return new CustomerAiConsultationCommand(
                REQUEST_ID,
                501,
                9002,
                new CustomerAiSubjectAssertionRequest(
                        42,
                        RolePolicy.CUSTOMER,
                        List.of("customer-ai.policy.read"),
                        REQUEST_ID,
                        501
                ),
                "sensitive question",
                List.of()
        );
    }

    private CustomerAiKnowledgeJobCommand knowledgeCommand() {
        return new CustomerAiKnowledgeJobCommand(
                REQUEST_ID,
                101,
                1,
                new CustomerAiKnowledgeJobCommand.Source(
                        URI.create("https://private-minio.example/knowledge.txt?signature=secret"),
                        "text/plain",
                        4
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

    private CustomerAiConsultationSummaryCommand summaryCommand() {
        return new CustomerAiConsultationSummaryCommand(
                REQUEST_ID,
                7001,
                501,
                ConsultationStatus.CLOSED,
                List.of(new CustomerAiConsultationSummaryCommand.Message(
                        CustomerAiConsultationSummaryCommand.SenderType.USER,
                        "sensitive summary source"
                ))
        );
    }

    private String consultationResponse() {
        return """
                {"schemaVersion":"1.0","requestId":"11111111-1111-4111-8111-111111111111",
                "decision":"ANSWER","answer":"sensitive answer","route":"POLICY","degraded":false,
                "handoffRequired":false,"evidence":[{"knowledgeVersionId":101,"chunkId":"safe-fixture","retrievalRank":1,"retrievalScore":0.9}]}
                """;
    }
}
