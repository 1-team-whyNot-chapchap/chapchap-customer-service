package com.chapchap.customer.domain.knowledge.processing;

import java.time.LocalDateTime;

public record KnowledgeProcessingRequest(
        Long knowledgeVersionId,
        int attempt,
        Source source,
        Metadata metadata,
        String chunkProfile
) {
    public record Source(String downloadUrl, String contentType, long fileSize) {
    }

    public record Metadata(
            String documentKey,
            String sourceService,
            String category,
            String version,
            LocalDateTime effectiveFrom
    ) {
    }
}
