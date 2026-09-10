package com.chapchap.customer.domain.consultation.dto.summary;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PreparedConsultationSummaryJob(
        UUID requestId,
        long summaryJobId,
        long consultationId,
        List<CustomerAiConsultationSummaryCommand.Message> messages
) {
    public PreparedConsultationSummaryJob {
        Objects.requireNonNull(requestId);
        if (summaryJobId <= 0 || consultationId <= 0) {
            throw new IllegalArgumentException("요약 Job identity는 양수여야 합니다.");
        }
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("요약할 메시지가 없습니다.");
        }
    }
}
