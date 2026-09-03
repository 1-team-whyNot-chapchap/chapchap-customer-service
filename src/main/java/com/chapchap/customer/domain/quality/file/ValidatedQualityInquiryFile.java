package com.chapchap.customer.domain.quality.file;

public record ValidatedQualityInquiryFile(
        String originalFilename,
        String contentType,
        long size,
        byte[] content
) {
}
