package com.chapchap.customer.domain.consultation.response;

import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.entity.ConsultationSenderType;

import java.time.LocalDateTime;

public record ConsultationMessageResponse(
        Long messageId,
        ConsultationSenderType senderType,
        int sequenceNo,
        String content,
        LocalDateTime createdAt
) {
    public static ConsultationMessageResponse from(ConsultationMessage message) {
        return new ConsultationMessageResponse(
                message.getId(),
                message.getSenderType(),
                message.getSequenceNo(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
