package com.chapchap.customer.domain.consultation.summary;

import java.util.List;

record CustomerAiConsultationSummaryRequest(
        String schemaVersion,
        long summaryJobId,
        long consultationId,
        List<Message> messages,
        Callback callback
) {
    private static final String CALLBACK_URI = "/internal/v1/consultation-summary-results";

    static CustomerAiConsultationSummaryRequest from(CustomerAiConsultationSummaryCommand command) {
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