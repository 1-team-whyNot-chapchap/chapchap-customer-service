package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.global.security.customerai.CustomerAiServiceTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;

public final class HttpCustomerAiConsultationSummaryClient implements CustomerAiConsultationSummaryClient {
    static final String PATH = "/internal/v1/consultation-summaries";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final CustomerAiServiceTokenProvider serviceTokenProvider;
    private final CustomerAiConsultationSummaryResponseParser responseParser;

    public HttpCustomerAiConsultationSummaryClient(
            RestClient restClient,
            CustomerAiServiceTokenProvider serviceTokenProvider,
            CustomerAiConsultationSummaryResponseParser responseParser
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.serviceTokenProvider = Objects.requireNonNull(serviceTokenProvider);
        this.responseParser = Objects.requireNonNull(responseParser);
    }

    @Override
    public CustomerAiConsultationSummaryAccepted submit(CustomerAiConsultationSummaryCommand command) {
        Objects.requireNonNull(command, "command must not be null.");
        String token = serviceToken();
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(REQUEST_ID_HEADER, command.requestId().toString())
                    .header(IDEMPOTENCY_KEY_HEADER, command.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CustomerAiConsultationSummaryRequest.from(command))
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().value() != 202) {
                throw new CustomerAiConsultationSummaryClientException(
                        CustomerAiConsultationSummaryClientException.Reason.CONTRACT_ERROR);
            }
            return responseParser.parse(
                    response.getBody(), command.summaryJobId(), command.consultationId());
        } catch (CustomerAiConsultationSummaryClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw classify(exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new CustomerAiConsultationSummaryClientException(isTimeout(exception)
                    ? CustomerAiConsultationSummaryClientException.Reason.TIMEOUT
                    : CustomerAiConsultationSummaryClientException.Reason.DEPENDENCY_UNAVAILABLE);
        }
    }

    private String serviceToken() {
        String token;
        try {
            token = serviceTokenProvider.getServiceToken();
        } catch (RuntimeException exception) {
            throw new CustomerAiConsultationSummaryClientException(
                    CustomerAiConsultationSummaryClientException.Reason.AUTHENTICATION_UNAVAILABLE);
        }
        if (token == null || token.isBlank() || token.chars().anyMatch(Character::isWhitespace)
                || token.split("\\.", -1).length != 3) {
            throw new CustomerAiConsultationSummaryClientException(
                    CustomerAiConsultationSummaryClientException.Reason.AUTHENTICATION_UNAVAILABLE);
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

    private CustomerAiConsultationSummaryClientException classify(int status) {
        CustomerAiConsultationSummaryClientException.Reason reason = switch (status) {
            case 400, 422 -> CustomerAiConsultationSummaryClientException.Reason.REQUEST_REJECTED;
            case 401, 403 -> CustomerAiConsultationSummaryClientException.Reason.AUTHENTICATION_REJECTED;
            case 409 -> CustomerAiConsultationSummaryClientException.Reason.IDEMPOTENCY_CONFLICT;
            case 504 -> CustomerAiConsultationSummaryClientException.Reason.TIMEOUT;
            default -> CustomerAiConsultationSummaryClientException.Reason.DEPENDENCY_UNAVAILABLE;
        };
        return new CustomerAiConsultationSummaryClientException(reason);
    }
}