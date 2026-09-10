package com.chapchap.customer.domain.knowledge.constant.processing.async;

public enum KnowledgeProcessingCallbackOutcome {
    APPLIED_COMPLETED,
    APPLIED_FAILED,
    IGNORED_DUPLICATE,
    IGNORED_STALE,
    CONFLICT
}