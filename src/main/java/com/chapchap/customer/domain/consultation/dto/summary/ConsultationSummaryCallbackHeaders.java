package com.chapchap.customer.domain.consultation.dto.summary;

import com.chapchap.customer.global.exception.consultation.summary.ConsultationSummaryCallbackException;

import java.util.UUID;

public record ConsultationSummaryCallbackHeaders(
        UUID requestId,
        long idempotencySummaryJobId
) {
    public ConsultationSummaryCallbackHeaders {
        if (requestId == null || idempotencySummaryJobId <= 0) {
            throw contractError();
        }
    }

    public static ConsultationSummaryCallbackHeaders parse(String requestId, String idempotencyKey) {
        try {
            if (requestId == null || idempotencyKey == null || !idempotencyKey.matches("[1-9][0-9]*")) {
                throw contractError();
            }
            return new ConsultationSummaryCallbackHeaders(
                    UUID.fromString(requestId),
                    Long.parseLong(idempotencyKey)
            );
        } catch (IllegalArgumentException exception) {
            throw contractError();
        }
    }

    private static ConsultationSummaryCallbackException contractError() {
        return new ConsultationSummaryCallbackException(
                ConsultationSummaryCallbackException.Reason.CONTRACT_ERROR);
    }
}