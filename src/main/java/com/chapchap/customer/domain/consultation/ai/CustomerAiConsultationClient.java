package com.chapchap.customer.domain.consultation.ai;

@FunctionalInterface
public interface CustomerAiConsultationClient {
    CustomerAiConsultationResult respond(CustomerAiConsultationCommand command);
}
