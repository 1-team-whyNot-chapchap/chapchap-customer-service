package com.chapchap.customer.domain.knowledge.processing.async;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class KnowledgeProcessingCallbackFingerprint {
    private KnowledgeProcessingCallbackFingerprint() {
    }

    static String create(KnowledgeProcessingCallback callback) {
        String canonical = String.join("|",
                Long.toString(callback.processingId()),
                Long.toString(callback.knowledgeVersionId()),
                callback.status().name(),
                value(callback.chunkCount()),
                value(callback.chunkProfile()),
                callback.failureCode() == null ? "" : callback.failureCode().name(),
                value(callback.retryable()));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
