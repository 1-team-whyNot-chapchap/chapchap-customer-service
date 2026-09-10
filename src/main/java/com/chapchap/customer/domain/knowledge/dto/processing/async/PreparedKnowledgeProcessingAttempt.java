package com.chapchap.customer.domain.knowledge.dto.processing.async;

import com.chapchap.customer.domain.knowledge.dto.processing.KnowledgeProcessingContext;

import java.util.Objects;
import java.util.UUID;

public record PreparedKnowledgeProcessingAttempt(
        UUID requestId,
        KnowledgeProcessingContext context
) {
    public PreparedKnowledgeProcessingAttempt {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(context);
    }
}
