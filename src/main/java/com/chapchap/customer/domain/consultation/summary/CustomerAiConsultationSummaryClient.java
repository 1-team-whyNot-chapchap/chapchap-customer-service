package com.chapchap.customer.domain.consultation.summary;

@FunctionalInterface
public interface CustomerAiConsultationSummaryClient {
    CustomerAiConsultationSummaryAccepted submit(CustomerAiConsultationSummaryCommand command);
}