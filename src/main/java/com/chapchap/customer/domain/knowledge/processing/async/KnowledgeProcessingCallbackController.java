package com.chapchap.customer.domain.knowledge.processing.async;

import com.chapchap.customer.global.security.customerai.CustomerAiCallbackJwtVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "customer.ai.callback-auth", name = "enabled", havingValue = "true")
@ConditionalOnBean(KnowledgeProcessingCallbackConsumer.class)
public final class KnowledgeProcessingCallbackController {
    public static final String PATH = "/internal/v1/knowledge-processing-results";

    private final CustomerAiCallbackJwtVerifier jwtVerifier;
    private final KnowledgeProcessingCallbackConsumer callbackConsumer;

    public KnowledgeProcessingCallbackController(
            CustomerAiCallbackJwtVerifier jwtVerifier,
            KnowledgeProcessingCallbackConsumer callbackConsumer
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
        KnowledgeProcessingCallbackHeaders headers = KnowledgeProcessingCallbackHeaders.parse(
                requestId, idempotencyKey);
        callbackConsumer.consumeVerified(headers, body);
        return ResponseEntity.noContent().build();
    }
}
