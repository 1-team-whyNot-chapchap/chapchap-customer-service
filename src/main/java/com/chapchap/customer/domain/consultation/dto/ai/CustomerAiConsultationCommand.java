package com.chapchap.customer.domain.consultation.dto.ai;

import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CustomerAiConsultationCommand(
        UUID requestId,
        long consultationId,
        long triggerMessageId,
        CustomerAiSubjectAssertionRequest subject,
        String message,
        List<String> conversationContext,
        List<Long> knowledgeVersionIds
) {
    private static final int MAX_MESSAGE_LENGTH = 10_000;
    private static final int MAX_CONTEXT_MESSAGES = 20;

    public CustomerAiConsultationCommand(UUID requestId, long consultationId, long triggerMessageId,
            CustomerAiSubjectAssertionRequest subject, String message, List<String> conversationContext) {
        this(requestId, consultationId, triggerMessageId, subject, message, conversationContext, List.of());
    }

    public CustomerAiConsultationCommand {
        Objects.requireNonNull(requestId, "requestId must not be null.");
        if (consultationId <= 0) {
            throw new IllegalArgumentException("consultationId must be a positive int64.");
        }
        if (triggerMessageId <= 0) {
            throw new IllegalArgumentException("triggerMessageId must be a positive int64.");
        }
        Objects.requireNonNull(subject, "subject must not be null.");
        if (!requestId.equals(subject.requestId()) || consultationId != subject.consultationId()) {
            throw new IllegalArgumentException("Subject request context must match the consultation command.");
        }
        if (subject.role() != RolePolicy.CUSTOMER && subject.role() != RolePolicy.RIDER) {
            throw new IllegalArgumentException("Only customer consultation roles are allowed.");
        }
        if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must contain between 1 and 10000 characters.");
        }
        Objects.requireNonNull(conversationContext, "conversationContext must not be null.");
        if (conversationContext.size() > MAX_CONTEXT_MESSAGES
                || conversationContext.stream().anyMatch(value ->
                value == null || value.isBlank() || value.length() > MAX_MESSAGE_LENGTH)) {
            throw new IllegalArgumentException("conversationContext contains an invalid message.");
        }
        conversationContext = List.copyOf(conversationContext);
        Objects.requireNonNull(knowledgeVersionIds, "knowledgeVersionIds must not be null.");
        if (knowledgeVersionIds.size() > 1000
                || knowledgeVersionIds.stream().anyMatch(id -> id == null || id <= 0)
                || knowledgeVersionIds.stream().distinct().count() != knowledgeVersionIds.size()) {
            throw new IllegalArgumentException("knowledgeVersionIds must contain at most 1000 unique positive int64 IDs.");
        }
        knowledgeVersionIds = List.copyOf(knowledgeVersionIds);
    }
}
