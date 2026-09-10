package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.dto.summary.CustomerAiConsultationSummaryCommand;
import com.chapchap.customer.domain.consultation.response.summary.CustomerAiConsultationSummaryAccepted;

@FunctionalInterface
public interface CustomerAiConsultationSummaryClient {
    CustomerAiConsultationSummaryAccepted submit(CustomerAiConsultationSummaryCommand command);
}