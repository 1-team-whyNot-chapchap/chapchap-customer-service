package com.chapchap.customer.domain.consultation.summary;

public enum ConsultationSummaryCallbackOutcome {
    APPLIED_COMPLETED,
    APPLIED_FAILED,
    IGNORED_DUPLICATE,
    IGNORED_STALE,
    CONFLICT
}