package com.chapchap.customer.domain.consultation.dto.event;

import com.chapchap.customer.global.security.constant.RolePolicy;

public record ConsultationAiResponseRequestedEvent(
        Long consultationId,
        Long triggerMessageId,
        Long userId,
        RolePolicy role
) {
    public ConsultationAiResponseRequestedEvent {
        if (consultationId == null || consultationId <= 0
                || triggerMessageId == null || triggerMessageId <= 0
                || userId == null || userId <= 0
                || (role != RolePolicy.CUSTOMER && role != RolePolicy.RIDER)) {
            throw new IllegalArgumentException("AI 상담 요청 문맥이 올바르지 않습니다.");
        }
    }
}
