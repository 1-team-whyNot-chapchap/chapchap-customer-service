package com.chapchap.customer.domain.knowledge.request.processing.async;

import com.chapchap.customer.domain.knowledge.dto.processing.async.CustomerAiKnowledgeJobCommand;

public record CustomerAiKnowledgeJobRequest(
        String schemaVersion,
        long knowledgeVersionId,
        int attempt,
        Source source,
        Metadata metadata,
        String chunkProfile,
        Callback callback
) {
    private static final String CALLBACK_URI = "/internal/v1/knowledge-processing-results";

    public static CustomerAiKnowledgeJobRequest from(CustomerAiKnowledgeJobCommand command) {
        return new CustomerAiKnowledgeJobRequest(
                "1.0",
                command.knowledgeVersionId(),
                command.attempt(),
                new Source(
                        command.source().downloadUrl().toString(),
                        command.source().contentType(),
                        command.source().fileSize()
                ),
                new Metadata(
                        command.metadata().documentKey(),
                        command.metadata().sourceService(),
                        command.metadata().category(),
                        command.metadata().version(),
                        command.metadata().effectiveFrom().toString()
                ),
                command.chunkProfile(),
                new Callback(CALLBACK_URI)
        );
    }

    record Source(String downloadUrl, String contentType, long fileSize) {
    }

    record Metadata(
            String documentKey,
            String sourceService,
            String category,
            String version,
            String effectiveFrom
    ) {
    }

    record Callback(String resultUri) {
    }
}
