package com.chapchap.customer.domain.consultation.request.summary;

import com.chapchap.customer.domain.consultation.dto.summary.CustomerAiConsultationSummaryCommand;

import java.util.List;

public record CustomerAiConsultationSummaryRequest(
        String schemaVersion,
        long summaryJobId,
        long consultationId,
        List<Message> messages,
        Callback callback
) {
    private static final String CALLBACK_URI = "/internal/v1/consultation-summary-results";

    public static CustomerAiConsultationSummaryRequest from(CustomerAiConsultationSummaryCommand command) {
        return new CustomerAiConsultationSummaryRequest(
                "1.0",
                command.summaryJobId(),
                command.consultationId(),
                command.messages().stream()
                        .map(message -> new Message(message.senderType().name(), message.content()))
                        .toList(),
                new Callback(CALLBACK_URI)
        );
    }

    record Message(String senderType, String content) {
    }

    record Callback(String resultUri) {
    }
}
