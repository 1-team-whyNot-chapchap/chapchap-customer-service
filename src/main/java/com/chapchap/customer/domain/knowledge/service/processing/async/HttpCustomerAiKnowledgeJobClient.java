package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.dto.processing.async.CustomerAiKnowledgeJobCommand;
import com.chapchap.customer.global.exception.knowledge.processing.async.CustomerAiKnowledgeJobClientException;
import com.chapchap.customer.domain.knowledge.request.processing.async.CustomerAiKnowledgeJobRequest;
import com.chapchap.customer.domain.knowledge.response.processing.async.CustomerAiKnowledgeJobAccepted;

import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiServiceTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;

public final class HttpCustomerAiKnowledgeJobClient implements CustomerAiKnowledgeJobClient {
    static final String PATH = "/internal/v1/knowledge-processings";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final CustomerAiServiceTokenProvider serviceTokenProvider;
    private final CustomerAiKnowledgeJobResponseParser responseParser;
    private final CustomerAiDiagnosticPublisher diagnostics;

    public HttpCustomerAiKnowledgeJobClient(
            RestClient restClient,
            CustomerAiServiceTokenProvider serviceTokenProvider,
            CustomerAiKnowledgeJobResponseParser responseParser
    ) {
        this(restClient, serviceTokenProvider, responseParser, CustomerAiDiagnosticPublisher.noOp());
    }

    public HttpCustomerAiKnowledgeJobClient(
            RestClient restClient,
            CustomerAiServiceTokenProvider serviceTokenProvider,
            CustomerAiKnowledgeJobResponseParser responseParser,
            CustomerAiDiagnosticPublisher diagnostics
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.serviceTokenProvider = Objects.requireNonNull(serviceTokenProvider);
        this.responseParser = Objects.requireNonNull(responseParser);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    @Override
    public CustomerAiKnowledgeJobAccepted submit(CustomerAiKnowledgeJobCommand command) {
        Objects.requireNonNull(command, "command must not be null.");
        try {
            CustomerAiKnowledgeJobAccepted accepted = doSubmit(command);
            diagnostics.publish(traceId -> CustomerAiDiagnosticEvent.knowledgeSubmitted(
                    command.requestId(), traceId, command.knowledgeVersionId(), accepted.processingId()));
            return accepted;
        } catch (CustomerAiKnowledgeJobClientException exception) {
            diagnostics.publish(traceId -> CustomerAiDiagnosticEvent.knowledgeSubmissionFailure(
                    command.requestId(),
                    traceId,
                    command.knowledgeVersionId(),
                    CustomerAiDiagnosticFailureCode.from(exception.reason()),
                    exception.retryable()
            ));
            throw exception;
        }
    }

    private CustomerAiKnowledgeJobAccepted doSubmit(CustomerAiKnowledgeJobCommand command) {
        String token = serviceToken();
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(REQUEST_ID_HEADER, command.requestId().toString())
                    .header(IDEMPOTENCY_KEY_HEADER, command.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CustomerAiKnowledgeJobRequest.from(command))
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().value() != 202) {
                throw new CustomerAiKnowledgeJobClientException(
                        CustomerAiKnowledgeJobClientException.Reason.CONTRACT_ERROR);
            }
            return responseParser.parse(response.getBody(), command.knowledgeVersionId());
        } catch (CustomerAiKnowledgeJobClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw classify(exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new CustomerAiKnowledgeJobClientException(isTimeout(exception)
                    ? CustomerAiKnowledgeJobClientException.Reason.TIMEOUT
                    : CustomerAiKnowledgeJobClientException.Reason.DEPENDENCY_UNAVAILABLE);
        }
    }

    private String serviceToken() {
        String token;
        try {
            token = serviceTokenProvider.getServiceToken();
        } catch (RuntimeException exception) {
            throw new CustomerAiKnowledgeJobClientException(
                    CustomerAiKnowledgeJobClientException.Reason.AUTHENTICATION_UNAVAILABLE);
        }
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace)
                || token.split("\\.", -1).length != 3) {
            throw new CustomerAiKnowledgeJobClientException(
                    CustomerAiKnowledgeJobClientException.Reason.AUTHENTICATION_UNAVAILABLE);
        }
        return token;
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

    private CustomerAiKnowledgeJobClientException classify(int status) {
        CustomerAiKnowledgeJobClientException.Reason reason = switch (status) {
            case 400, 422 -> CustomerAiKnowledgeJobClientException.Reason.REQUEST_REJECTED;
            case 401, 403 -> CustomerAiKnowledgeJobClientException.Reason.AUTHENTICATION_REJECTED;
            case 409 -> CustomerAiKnowledgeJobClientException.Reason.IDEMPOTENCY_CONFLICT;
            case 504 -> CustomerAiKnowledgeJobClientException.Reason.TIMEOUT;
            default -> CustomerAiKnowledgeJobClientException.Reason.DEPENDENCY_UNAVAILABLE;
        };
        return new CustomerAiKnowledgeJobClientException(reason);
    }
}
