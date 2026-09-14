package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationCommand;
import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationResult;
import com.chapchap.customer.global.exception.consultation.ai.CustomerAiConsultationClientException;
import com.chapchap.customer.domain.consultation.request.ai.CustomerAiConsultationRequest;

import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticOutcome;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.global.exception.customerai.security.CustomerAiAuthenticationUnavailableException;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiRequestCredentials;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiRequestCredentialsProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.Objects;

public final class HttpCustomerAiConsultationClient implements CustomerAiConsultationClient {
    static final String PATH = "/internal/v1/consultation-responses";
    static final String SUBJECT_ASSERTION_HEADER = "X-Subject-Assertion";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final CustomerAiRequestCredentialsProvider credentialsProvider;
    private final CustomerAiConsultationResponseParser responseParser;
    private final CustomerAiDiagnosticPublisher diagnostics;
    private boolean boundariesEnabled;

    public HttpCustomerAiConsultationClient withBoundaries(boolean enabled) {
        this.boundariesEnabled = enabled;
        return this;
    }
    @Override
    public boolean usesBoundaries() { return boundariesEnabled; }

    @Override
    public CustomerAiConsultationCommand prepare(CustomerAiConsultationCommand command) {
        if (!boundariesEnabled) return command;
        var subject = new com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest(
                command.subject().userId(), command.subject().role(),
                java.util.List.of("customer-ai.policy.read"), command.requestId(), command.consultationId());
        var initial = new CustomerAiConsultationCommand(command.requestId(), command.consultationId(),
                command.triggerMessageId(), subject, command.message(), command.conversationContext(),
                command.knowledgeVersionIds());
        var credentials = credentialsProvider.create(subject);
        try {
            String body = restClient.post().uri("/internal/v1/consultation-interpretations")
                    .header(HttpHeaders.AUTHORIZATION, credentials.authorization())
                    .header(SUBJECT_ASSERTION_HEADER, credentials.subjectAssertion())
                    .header(REQUEST_ID_HEADER, command.requestId().toString())
                    .contentType(MediaType.APPLICATION_JSON).body(CustomerAiConsultationRequest.from(initial))
                    .retrieve().body(String.class);
            return ConsultationPlanParser.parse(body, initial);
        } catch (RestClientResponseException error) {
            throw classify(error.getStatusCode().value());
        } catch (RestClientException error) {
            throw new CustomerAiConsultationClientException(isTimeout(error)
                    ? CustomerAiConsultationClientException.Reason.TIMEOUT
                    : CustomerAiConsultationClientException.Reason.DEPENDENCY_UNAVAILABLE);
        }
    }

    public HttpCustomerAiConsultationClient(
            RestClient restClient,
            CustomerAiRequestCredentialsProvider credentialsProvider,
            CustomerAiConsultationResponseParser responseParser
    ) {
        this(restClient, credentialsProvider, responseParser, CustomerAiDiagnosticPublisher.noOp());
    }

    public HttpCustomerAiConsultationClient(
            RestClient restClient,
            CustomerAiRequestCredentialsProvider credentialsProvider,
            CustomerAiConsultationResponseParser responseParser,
            CustomerAiDiagnosticPublisher diagnostics
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
        this.responseParser = Objects.requireNonNull(responseParser);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    @Override
    public CustomerAiConsultationResult respond(CustomerAiConsultationCommand command) {
        Objects.requireNonNull(command, "command must not be null.");
        try {
            CustomerAiConsultationResult result = doRespond(command);
            diagnostics.publish(traceId -> CustomerAiDiagnosticEvent.consultationResult(
                    command.requestId(),
                    traceId,
                    command.consultationId(),
                    result.route(),
                    CustomerAiDiagnosticOutcome.valueOf(result.decision().name())
            ));
            return result;
        } catch (CustomerAiConsultationClientException exception) {
            emitFailure(command, CustomerAiDiagnosticFailureCode.from(exception.reason()), exception.retryable());
            throw exception;
        } catch (CustomerAiAuthenticationUnavailableException exception) {
            emitFailure(command, CustomerAiDiagnosticFailureCode.AUTHENTICATION_UNAVAILABLE, false);
            throw exception;
        }
    }

    private CustomerAiConsultationResult doRespond(CustomerAiConsultationCommand command) {
        CustomerAiRequestCredentials credentials = credentialsProvider.create(command.subject());
        CustomerAiConsultationRequest request = CustomerAiConsultationRequest.from(command);
        String responseBody;

        try {
            responseBody = restClient.post()
                    .uri(command.planId() == null ? PATH : "/internal/v1/consultation-plan-responses")
                    .header(HttpHeaders.AUTHORIZATION, credentials.authorization())
                    .header(SUBJECT_ASSERTION_HEADER, credentials.subjectAssertion())
                    .header(REQUEST_ID_HEADER, command.requestId().toString())
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey(command))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(command.planId() == null ? request : java.util.Map.of(
                            "planId", command.planId(), "request", request))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw classify(exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new CustomerAiConsultationClientException(isTimeout(exception)
                    ? CustomerAiConsultationClientException.Reason.TIMEOUT
                    : CustomerAiConsultationClientException.Reason.DEPENDENCY_UNAVAILABLE);
        }

        return responseParser.parse(responseBody, command.requestId());
    }

    private void emitFailure(
            CustomerAiConsultationCommand command,
            CustomerAiDiagnosticFailureCode failureCode,
            boolean retryable
    ) {
        diagnostics.publish(traceId -> CustomerAiDiagnosticEvent.consultationFailure(
                command.requestId(), traceId, command.consultationId(), failureCode, retryable));
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String idempotencyKey(CustomerAiConsultationCommand command) {
        return "consultation-response:" + command.consultationId() + ":" + command.triggerMessageId();
    }

    private CustomerAiConsultationClientException classify(int status) {
        CustomerAiConsultationClientException.Reason reason = switch (status) {
            case 400 -> CustomerAiConsultationClientException.Reason.REQUEST_REJECTED;
            case 401, 403 -> CustomerAiConsultationClientException.Reason.AUTHENTICATION_REJECTED;
            case 409 -> CustomerAiConsultationClientException.Reason.IDEMPOTENCY_CONFLICT;
            case 422 -> CustomerAiConsultationClientException.Reason.UNSAFE_RESPONSE;
            case 504 -> CustomerAiConsultationClientException.Reason.TIMEOUT;
            default -> CustomerAiConsultationClientException.Reason.DEPENDENCY_UNAVAILABLE;
        };
        return new CustomerAiConsultationClientException(reason);
    }
}
