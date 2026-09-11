package com.chapchap.customer.domain.consultation.request.ai;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationCommand;

import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;

import java.util.List;
import java.util.UUID;

public record CustomerAiConsultationRequest(
        String schemaVersion,
        UUID requestId,
        long consultationId,
        long triggerMessageId,
        Subject subject,
        String message,
        List<String> conversationContext,
        List<Long> knowledgeVersionIds
) {
    public static CustomerAiConsultationRequest from(CustomerAiConsultationCommand command) {
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
                command.conversationContext(),
                command.knowledgeVersionIds()
        );
    }

    record Subject(long userId, String role, List<String> allowedAiScopes) {
    }
}
