package com.chapchap.customer.domain.knowledge.processing.async;

import java.util.Objects;

public final class KnowledgeProcessingCallbackConsumer {
    private final KnowledgeProcessingCallbackParser parser;
    private final KnowledgeProcessingCallbackStatePort statePort;

    public KnowledgeProcessingCallbackConsumer(
            KnowledgeProcessingCallbackParser parser,
            KnowledgeProcessingCallbackStatePort statePort
    ) {
        this.parser = Objects.requireNonNull(parser);
        this.statePort = Objects.requireNonNull(statePort);
    }

    public KnowledgeProcessingCallbackOutcome consumeVerified(
            KnowledgeProcessingCallbackHeaders headers,
            String body
    ) {
        KnowledgeProcessingCallback callback = parser.parse(body, headers);
        KnowledgeProcessingCallbackOutcome outcome = statePort.applyAtomically(headers, callback);
        if (outcome == null || outcome == KnowledgeProcessingCallbackOutcome.CONFLICT) {
            throw new KnowledgeProcessingCallbackException(
                    KnowledgeProcessingCallbackException.Reason.STATE_CONFLICT);
        }
        return outcome;
    }
}