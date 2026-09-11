package com.chapchap.customer.domain.quality.response;

import com.chapchap.customer.domain.quality.entity.QualityInquiryAttachment;

import java.time.LocalDateTime;

public record QualityInquiryAttachmentResponse(
        Long attachmentId,
        String originalFilename,
        String contentType,
        long fileSize,
        LocalDateTime createdAt
) {
    public static QualityInquiryAttachmentResponse from(QualityInquiryAttachment attachment) {
        return new QualityInquiryAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getCreatedAt()
        );
    }
}
