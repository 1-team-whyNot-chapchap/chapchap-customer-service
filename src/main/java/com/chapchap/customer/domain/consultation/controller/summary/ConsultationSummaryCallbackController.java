package com.chapchap.customer.domain.consultation.controller.summary;

import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallbackHeaders;
import com.chapchap.customer.domain.consultation.service.summary.ConsultationSummaryCallbackConsumer;

import com.chapchap.customer.domain.customerai.service.security.CustomerAiCallbackJwtVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "customer.ai.callback-auth", name = "enabled", havingValue = "true")
@ConditionalOnBean(ConsultationSummaryCallbackConsumer.class)
public final class ConsultationSummaryCallbackController {
    public static final String PATH = "/internal/v1/consultation-summary-results";

    private final CustomerAiCallbackJwtVerifier jwtVerifier;
    private final ConsultationSummaryCallbackConsumer callbackConsumer;

    public ConsultationSummaryCallbackController(
            CustomerAiCallbackJwtVerifier jwtVerifier,
            ConsultationSummaryCallbackConsumer callbackConsumer
    ) {
        this.jwtVerifier = jwtVerifier;
        this.callbackConsumer = callbackConsumer;
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody String body
    ) {
        jwtVerifier.verify(authorization);
        ConsultationSummaryCallbackHeaders headers = ConsultationSummaryCallbackHeaders.parse(
                requestId, idempotencyKey);
        callbackConsumer.consumeVerified(headers, body);
        return ResponseEntity.noContent().build();
    }
}
