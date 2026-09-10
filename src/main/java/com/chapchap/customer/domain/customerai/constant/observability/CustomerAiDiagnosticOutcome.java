package com.chapchap.customer.domain.customerai.constant.observability;

public enum CustomerAiDiagnosticOutcome {
    ANSWER,
    HANDOFF,
    DEGRADED,
    ACCEPTED,
    COMPLETED,
    FAILED,
    IGNORED_DUPLICATE,
    IGNORED_STALE,
    ACCESS_GRANTED,
    ACCESS_DENIED
}
