package com.chapchap.customer.domain.knowledge.processing.async;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record CustomerAiKnowledgeJobCommand(
        UUID requestId,
        long knowledgeVersionId,
        int attempt,
        Source source,
        Metadata metadata,
        String chunkProfile
) {
    public static final String SUPPORTED_CHUNK_PROFILE = "HYBRID_POLICY_V1";

    public CustomerAiKnowledgeJobCommand {
        Objects.requireNonNull(requestId, "requestId must not be null.");
        if (knowledgeVersionId <= 0) {
            throw new IllegalArgumentException("knowledgeVersionId must be positive.");
        }
        if (attempt < 1 || attempt > 3) {
            throw new IllegalArgumentException("attempt must be between 1 and 3.");
        }
        Objects.requireNonNull(source, "source must not be null.");
        Objects.requireNonNull(metadata, "metadata must not be null.");
        if (!SUPPORTED_CHUNK_PROFILE.equals(chunkProfile)) {
            throw new IllegalArgumentException("Unsupported chunkProfile.");
        }
    }

    public String idempotencyKey() {
        return knowledgeVersionId + ":" + chunkProfile;
    }

    public record Source(URI downloadUrl, String contentType, long fileSize) {
        public Source {
            Objects.requireNonNull(downloadUrl, "downloadUrl must not be null.");
            if (!downloadUrl.isAbsolute()
                    || !"https".equalsIgnoreCase(downloadUrl.getScheme())
                    || downloadUrl.getHost() == null
                    || downloadUrl.getUserInfo() != null
                    || downloadUrl.getFragment() != null) {
                throw new IllegalArgumentException("downloadUrl must be an allowed HTTPS URI shape.");
            }
            requireText(contentType, "contentType", 255);
            if (fileSize <= 0) {
                throw new IllegalArgumentException("fileSize must be positive.");
            }
        }
    }

    public record Metadata(
            String documentKey,
            String sourceService,
            String category,
            String version,
            OffsetDateTime effectiveFrom
    ) {
        public Metadata {
            requireText(documentKey, "documentKey", 100);
            requireText(sourceService, "sourceService", 100);
            requireText(category, "category", 50);
            requireText(version, "version", 100);
            Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null.");
        }
    }

    private static void requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
    }
}