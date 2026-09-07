package com.chapchap.customer.global.security.customerai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "customer.ai.internal-auth", name = "enabled", havingValue = "true")
public class CustomerAiSubjectJwksController {
    public static final String PATH = "/.well-known/customer-ai-subject-jwks.json";

    private final CustomerAiSubjectJwksDocument document;

    public CustomerAiSubjectJwksController(CustomerAiSubjectJwksDocument document) {
        this.document = document;
    }

    @GetMapping(PATH)
    public ResponseEntity<CustomerAiSubjectJwksDocument> keys() {
        return ResponseEntity.ok(document);
    }
}
