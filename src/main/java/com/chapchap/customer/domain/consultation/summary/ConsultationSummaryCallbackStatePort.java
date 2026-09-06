package com.chapchap.customer.domain.consultation.summary;

public interface ConsultationSummaryCallbackStatePort {
    ConsultationSummaryCallbackOutcome applyAtomically(
            ConsultationSummaryCallbackHeaders headers,
            ConsultationSummaryCallback callback
    );
}