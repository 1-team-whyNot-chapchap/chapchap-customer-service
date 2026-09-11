package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryCallbackOutcome;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallbackHeaders;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryJobSnapshot;

public final class ConsultationSummaryCallbackStateMachine {
    public ConsultationSummaryCallbackOutcome decide(
            ConsultationSummaryJobSnapshot current,
            ConsultationSummaryCallbackHeaders headers,
            ConsultationSummaryCallback callback
    ) {
        if (current == null || current.summaryJobId() != callback.summaryJobId()) {
            return ConsultationSummaryCallbackOutcome.IGNORED_STALE;
        }
        if (!current.requestId().equals(headers.requestId())
                || current.consultationId() != callback.consultationId()) {
            return ConsultationSummaryCallbackOutcome.CONFLICT;
        }
        if (current.terminalCallback() != null) {
            return current.terminalCallback().equals(callback)
                    ? ConsultationSummaryCallbackOutcome.IGNORED_DUPLICATE
                    : ConsultationSummaryCallbackOutcome.CONFLICT;
        }
        return callback.status() == ConsultationSummaryCallback.Status.COMPLETED
                ? ConsultationSummaryCallbackOutcome.APPLIED_COMPLETED
                : ConsultationSummaryCallbackOutcome.APPLIED_FAILED;
    }
}