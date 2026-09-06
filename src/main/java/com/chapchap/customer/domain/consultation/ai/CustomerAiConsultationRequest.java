package com.chapchap.customer.domain.consultation.ai;

import com.chapchap.customer.global.security.customerai.CustomerAiSubjectAssertionRequest;

import java.util.List;
import java.util.UUID;

record CustomerAiConsultationRequest(
        String schemaVersion,
        UUID requestId,
        long consultationId,
        long triggerMessageId,
        Subject subject,
        String message,
        List<String> conversationContext
) {
    static CustomerAiConsultationRequest from(CustomerAiConsultationCommand command) {
        CustomerAiSubjectAssertionRequest authenticatedSubject = command.subject();
        return new CustomerAiConsultationRequest(
                "1.0",
                command.requestId(),
                command.consultationId(),
                command.triggerMessageId(),
                new Subject(
                        authenticatedSubject.userId(),
                        authenticatedSubject.role().getRole(),
                        authenticatedSubject.allowedAiScopes()
                ),
                command.message(),
                command.conversationContext()
        );
    }

    record Subject(long userId, String role, List<String> allowedAiScopes) {
    }
}
