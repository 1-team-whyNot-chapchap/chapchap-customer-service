package com.chapchap.customer.domain.knowledge.processing.async;

public enum KnowledgeProcessingAttemptStatus {
    CREATED,
    SUBMITTED,
    ACCEPTED,
    SUPERSEDED,
    TERMINAL,
    SUBMISSION_FAILED
}
