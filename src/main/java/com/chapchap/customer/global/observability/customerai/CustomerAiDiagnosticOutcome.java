package com.chapchap.customer.global.observability.customerai;

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
