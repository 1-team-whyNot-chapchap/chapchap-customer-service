package com.chapchap.customer.domain.knowledge.processing.async;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CustomerAiKnowledgeJobResponseParser {
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "processingId", "knowledgeVersionId", "status"
    );

    private final ObjectMapper objectMapper;

    public CustomerAiKnowledgeJobResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public CustomerAiKnowledgeJobAccepted parse(String body, long expectedKnowledgeVersionId) {
        if (body == null || body.isBlank() || expectedKnowledgeVersionId <= 0) {
            throw contractError();
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node == null || !node.isObject()
                    || !new HashSet<>(node.propertyNames()).equals(FIELDS)
                    || !"1.0".equals(requiredText(node, "schemaVersion"))
                    || !"ACCEPTED".equals(requiredText(node, "status"))) {
                throw contractError();
            }
            long processingId = requiredPositiveLong(node, "processingId");
            long knowledgeVersionId = requiredPositiveLong(node, "knowledgeVersionId");
            if (knowledgeVersionId != expectedKnowledgeVersionId) {
                throw contractError();
            }
            return new CustomerAiKnowledgeJobAccepted(processingId, knowledgeVersionId);
        } catch (CustomerAiKnowledgeJobClientException exception) {
            throw exception;
        } catch (Exception exception) {
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

    private long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw contractError();
        }
        return value.longValue();
    }

    private CustomerAiKnowledgeJobClientException contractError() {
        return new CustomerAiKnowledgeJobClientException(
                CustomerAiKnowledgeJobClientException.Reason.CONTRACT_ERROR);
    }
}