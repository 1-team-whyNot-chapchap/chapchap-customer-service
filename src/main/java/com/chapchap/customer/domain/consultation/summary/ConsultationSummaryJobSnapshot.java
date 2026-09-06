package com.chapchap.customer.domain.consultation.summary;

import com.chapchap.customer.domain.consultation.entity.ConsultationStatus;

import java.util.Objects;
import java.util.UUID;

public record ConsultationSummaryJobSnapshot(
        UUID requestId,
        long summaryJobId,
        long consultationId,
        ConsultationStatus consultationStatus,
        ConsultationSummaryCallback terminalCallback
) {
    public ConsultationSummaryJobSnapshot {
        Objects.requireNonNull(requestId);
        if (summaryJobId <= 0 || consultationId <= 0) {
            throw new IllegalArgumentException("Summary job identity is invalid.");
        }
        if (consultationStatus != ConsultationStatus.CLOSED) {
            throw new IllegalArgumentException("Summary job must remain attached to a CLOSED consultation.");
        }
        if (terminalCallback != null
                && (terminalCallback.summaryJobId() != summaryJobId
                || terminalCallback.consultationId() != consultationId)) {
            throw new IllegalArgumentException("Terminal callback identity does not match the summary job.");
        }
    }
}