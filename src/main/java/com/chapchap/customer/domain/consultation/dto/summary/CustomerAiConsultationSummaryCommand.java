package com.chapchap.customer.domain.consultation.dto.summary;

import com.chapchap.customer.domain.consultation.constant.ConsultationStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CustomerAiConsultationSummaryCommand(
        UUID requestId,
        long summaryJobId,
        long consultationId,
        ConsultationStatus consultationStatus,
        List<Message> messages
) {
    private static final int MAX_MESSAGES = 500;
    private static final int MAX_CONTENT_LENGTH = 10_000;

    public CustomerAiConsultationSummaryCommand {
        Objects.requireNonNull(requestId, "requestId must not be null.");
        if (summaryJobId <= 0 || consultationId <= 0) {
            throw new IllegalArgumentException("Summary job identity must be positive.");
        }
        if (consultationStatus == null || consultationStatus == ConsultationStatus.AI_HANDLING) {
            throw new IllegalArgumentException("Consultation must be handed off or CLOSED before summary submission.");
        }
        Objects.requireNonNull(messages, "messages must not be null.");
        if (messages.isEmpty() || messages.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("messages size is invalid.");
        }
        messages = List.copyOf(messages);
    }

    public String idempotencyKey() {
        return "consultation-summary:" + consultationId;
    }

    public record Message(SenderType senderType, String content) {
        public Message {
            Objects.requireNonNull(senderType, "senderType must not be null.");
            if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
                throw new IllegalArgumentException("Summary message content is invalid.");
            }
        }
    }

    public enum SenderType {
        USER,
        ADMIN,
        AI
    }
}