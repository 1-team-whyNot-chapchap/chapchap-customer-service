package com.chapchap.customer.domain.knowledge.processing.async;

public final class KnowledgeProcessingCallbackStateMachine {
    public KnowledgeProcessingCallbackOutcome decide(
            KnowledgeProcessingJobSnapshot current,
            KnowledgeProcessingCallbackHeaders headers,
            KnowledgeProcessingCallback callback
    ) {
        if (current == null || current.processingId() != callback.processingId()) {
            return KnowledgeProcessingCallbackOutcome.IGNORED_STALE;
        }
        if (!current.requestId().equals(headers.requestId())
                || current.knowledgeVersionId() != callback.knowledgeVersionId()) {
            return KnowledgeProcessingCallbackOutcome.CONFLICT;
        }
        if (current.terminalCallback() != null) {
            return current.terminalCallback().equals(callback)
                    ? KnowledgeProcessingCallbackOutcome.IGNORED_DUPLICATE
                    : KnowledgeProcessingCallbackOutcome.CONFLICT;
        }
        if (callback.status() == KnowledgeProcessingCallback.Status.COMPLETED
                && !current.chunkProfile().equals(callback.chunkProfile())) {
            return KnowledgeProcessingCallbackOutcome.CONFLICT;
        }
        return callback.status() == KnowledgeProcessingCallback.Status.COMPLETED
                ? KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED
                : KnowledgeProcessingCallbackOutcome.APPLIED_FAILED;
    }
}