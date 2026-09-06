package com.chapchap.customer.domain.knowledge.processing.async;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class KnowledgeProcessingCallbackParser {
    private static final Set<String> COMMON_FIELDS = Set.of(
            "schemaVersion", "processingId", "knowledgeVersionId", "status"
    );
    private final ObjectMapper objectMapper;

    public KnowledgeProcessingCallbackParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public KnowledgeProcessingCallback parse(String body, KnowledgeProcessingCallbackHeaders headers) {
        if (body == null || body.isBlank() || headers == null) {
            throw contractError();
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            requireObject(node);
            KnowledgeProcessingCallback.Status status = status(node);
            Set<String> expected = new HashSet<>(COMMON_FIELDS);
            if (status == KnowledgeProcessingCallback.Status.COMPLETED) {
                expected.add("chunkCount");
                expected.add("chunkProfile");
            } else {
                expected.add("failureCode");
                expected.add("retryable");
            }
            if (!new HashSet<>(node.propertyNames()).equals(expected)
                    || !"1.0".equals(requiredText(node, "schemaVersion"))) {
                throw contractError();
            }
            long processingId = requiredPositiveLong(node, "processingId");
            long knowledgeVersionId = requiredPositiveLong(node, "knowledgeVersionId");
            if (processingId != headers.idempotencyProcessingId()) {
                throw contractError();
            }
            if (status == KnowledgeProcessingCallback.Status.COMPLETED) {
                int chunkCount = requiredPositiveInt(node, "chunkCount");
                String chunkProfile = requiredText(node, "chunkProfile");
                if (!CustomerAiKnowledgeJobCommand.SUPPORTED_CHUNK_PROFILE.equals(chunkProfile)) {
                    throw contractError();
                }
                return new KnowledgeProcessingCallback(
                        processingId, knowledgeVersionId, status, chunkCount, chunkProfile, null, null);
            }
            KnowledgeProcessingCallback.FailureCode failureCode = enumValue(
                    requiredText(node, "failureCode"), KnowledgeProcessingCallback.FailureCode.class);
            boolean retryable = requiredBoolean(node, "retryable");
            return new KnowledgeProcessingCallback(
                    processingId, knowledgeVersionId, status, null, null, failureCode, retryable);
        } catch (KnowledgeProcessingCallbackException exception) {
            throw exception;
        } catch (Exception exception) {
            throw contractError();
        }
    }

    private KnowledgeProcessingCallback.Status status(JsonNode node) {
        return enumValue(requiredText(node, "status"), KnowledgeProcessingCallback.Status.class);
    }

    private void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw contractError();
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            throw contractError();
        }
        return value.stringValue();
    }

    private boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw contractError();
        }
        return value.booleanValue();
    }

    private long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw contractError();
        }
        return value.longValue();
    }

    private int requiredPositiveInt(JsonNode node, String field) {
        long value = requiredPositiveLong(node, field);
        if (value > Integer.MAX_VALUE) {
            throw contractError();
        }
        return (int) value;
    }

    private <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw contractError();
        }
    }

    private KnowledgeProcessingCallbackException contractError() {
        return new KnowledgeProcessingCallbackException(
                KnowledgeProcessingCallbackException.Reason.CONTRACT_ERROR);
    }
}