package com.chapchap.customer.domain.quality.response;

import com.chapchap.customer.domain.quality.entity.QualityInquiry;
import com.chapchap.customer.domain.quality.entity.QualityInquiryStatus;
import com.chapchap.customer.domain.quality.entity.QualityInquiryType;

import java.time.LocalDateTime;

public record QualityInquirySummaryResponse(
        Long qualityInquiryId,
        Long userId,
        QualityInquiryType inquiryType,
        QualityInquiryStatus status,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static QualityInquirySummaryResponse from(QualityInquiry inquiry) {
        return new QualityInquirySummaryResponse(
                inquiry.getId(),
                inquiry.getUserId(),
                inquiry.getInquiryType(),
                inquiry.getStatus(),
                inquiry.getPriority(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()
        );
    }
}
