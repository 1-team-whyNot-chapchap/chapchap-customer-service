package com.chapchap.customer.domain.knowledge.file;

public record ValidatedKnowledgeFile(
        String originalFilename,
        String contentType,
        long size,
        byte[] content
) {
}
