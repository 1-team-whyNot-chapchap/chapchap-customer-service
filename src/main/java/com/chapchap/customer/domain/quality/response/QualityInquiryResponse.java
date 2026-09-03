package com.chapchap.customer.domain.quality.response;

import com.chapchap.customer.domain.quality.entity.QualityInquiry;
import com.chapchap.customer.domain.quality.entity.QualityInquiryStatus;
import com.chapchap.customer.domain.quality.entity.QualityInquiryType;

import java.time.LocalDateTime;
import java.util.List;

public record QualityInquiryResponse(
        Long qualityInquiryId,
        Long userId,
        Long orderId,
        Long productId,
        String deliveryId,
        QualityInquiryType inquiryType,
        QualityInquiryStatus status,
        String priority,
        String content,
        String summary,
        Long assignedAdminId,
        String adminAnswer,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<QualityInquiryAttachmentResponse> attachments
) {
    public static QualityInquiryResponse from(QualityInquiry inquiry) {
        return new QualityInquiryResponse(
                inquiry.getId(),
                inquiry.getUserId(),
                inquiry.getOrderId(),
                inquiry.getProductId(),
                inquiry.getDeliveryId(),
                inquiry.getInquiryType(),
                inquiry.getStatus(),
                inquiry.getPriority(),
                inquiry.getContent(),
                inquiry.getSummary(),
                inquiry.getAssignedAdminId(),
                inquiry.getAdminAnswer(),
                inquiry.getResolvedAt(),
                inquiry.getClosedAt(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt(),
                inquiry.getAttachments().stream().map(QualityInquiryAttachmentResponse::from).toList()
        );
    }
}
