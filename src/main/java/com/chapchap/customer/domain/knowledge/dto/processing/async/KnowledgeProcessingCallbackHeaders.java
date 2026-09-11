package com.chapchap.customer.domain.knowledge.dto.processing.async;

import com.chapchap.customer.global.exception.knowledge.processing.async.KnowledgeProcessingCallbackException;

import java.util.UUID;

public record KnowledgeProcessingCallbackHeaders(
        UUID requestId,
        long idempotencyProcessingId
) {
    public KnowledgeProcessingCallbackHeaders {
        if (requestId == null || idempotencyProcessingId <= 0) {
            throw contractError();
        }
    }

    public static KnowledgeProcessingCallbackHeaders parse(String requestId, String idempotencyKey) {
        try {
            if (requestId == null || idempotencyKey == null || !idempotencyKey.matches("[1-9][0-9]*")) {
                throw contractError();
            }
            return new KnowledgeProcessingCallbackHeaders(
                    UUID.fromString(requestId),
                    Long.parseLong(idempotencyKey)
            );
        } catch (IllegalArgumentException exception) {
            throw contractError();
        }
    }

    private static KnowledgeProcessingCallbackException contractError() {
        return new KnowledgeProcessingCallbackException(
                KnowledgeProcessingCallbackException.Reason.CONTRACT_ERROR);
    }
}