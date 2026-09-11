package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryCallbackOutcome;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallbackHeaders;

public interface ConsultationSummaryCallbackStatePort {
    ConsultationSummaryCallbackOutcome applyAtomically(
            ConsultationSummaryCallbackHeaders headers,
            ConsultationSummaryCallback callback
    );
}