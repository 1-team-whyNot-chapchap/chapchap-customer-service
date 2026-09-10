package com.chapchap.customer.domain.quality.dto.file;

public record ValidatedQualityInquiryFile(
        String originalFilename,
        String contentType,
        long size,
        byte[] content
) {
}
