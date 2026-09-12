package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationCommand;
import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationResult;

@FunctionalInterface
public interface CustomerAiConsultationClient {
    CustomerAiConsultationResult respond(CustomerAiConsultationCommand command);
    default CustomerAiConsultationCommand prepare(CustomerAiConsultationCommand command) {
        return command;
    }
    default boolean usesBoundaries() { return false; }
}
