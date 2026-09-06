package com.chapchap.customer.domain.consultation.ai;

import com.chapchap.customer.global.security.customerai.CustomerAiRequestCredentials;
import com.chapchap.customer.global.security.customerai.CustomerAiRequestCredentialsProvider;
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

    public HttpCustomerAiConsultationClient(
            RestClient restClient,
            CustomerAiRequestCredentialsProvider credentialsProvider,
            CustomerAiConsultationResponseParser responseParser
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider);
        this.responseParser = Objects.requireNonNull(responseParser);
    }

    @Override
    public CustomerAiConsultationResult respond(CustomerAiConsultationCommand command) {
        Objects.requireNonNull(command, "command must not be null.");
        CustomerAiRequestCredentials credentials = credentialsProvider.create(command.subject());
        CustomerAiConsultationRequest request = CustomerAiConsultationRequest.from(command);
        String responseBody;

        try {
            responseBody = restClient.post()
                    .uri(PATH)
                    .header(HttpHeaders.AUTHORIZATION, credentials.authorization())
                    .header(SUBJECT_ASSERTION_HEADER, credentials.subjectAssertion())
                    .header(REQUEST_ID_HEADER, command.requestId().toString())
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey(command))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
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
