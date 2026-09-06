package com.chapchap.customer.domain.knowledge.processing.async;

public interface KnowledgeProcessingCallbackStatePort {
    KnowledgeProcessingCallbackOutcome applyAtomically(
            KnowledgeProcessingCallbackHeaders headers,
            KnowledgeProcessingCallback callback
    );
}