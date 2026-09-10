package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationResult;
import com.chapchap.customer.global.exception.consultation.ai.CustomerAiConsultationClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CustomerAiConsultationResponseParser {
    private static final Set<String> COMMON_FIELDS = Set.of(
            "schemaVersion", "requestId", "decision", "route", "degraded", "handoffRequired", "evidence"
    );
    private static final int MAX_ANSWER_LENGTH = 10_000;
    private static final int MAX_CHUNK_ID_LENGTH = 200;
    private static final int MAX_EVIDENCE = 5;

    private final ObjectMapper objectMapper;

    public CustomerAiConsultationResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    public CustomerAiConsultationResult parse(String body, UUID expectedRequestId) {
        if (body == null || body.isBlank() || expectedRequestId == null) {
            throw contractError();
        }
        try {
            return parseNode(objectMapper.readTree(body), expectedRequestId);
        } catch (CustomerAiConsultationClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw contractError();
        }
    }

    private CustomerAiConsultationResult parseNode(JsonNode node, UUID expectedRequestId) {
        requireObject(node);
        CustomerAiConsultationResult.Decision decision = enumValue(
                requiredText(node, "decision"), CustomerAiConsultationResult.Decision.class);
        assertExactFields(node, decision == CustomerAiConsultationResult.Decision.HANDOFF
                ? COMMON_FIELDS
                : withAnswer(COMMON_FIELDS));

        if (!"1.0".equals(requiredText(node, "schemaVersion"))) {
            throw contractError();
        }
        UUID requestId = parseUuid(requiredText(node, "requestId"));
        if (!expectedRequestId.equals(requestId)) {
            throw contractError();
        }
        CustomerAiConsultationResult.Route route = enumValue(
                requiredText(node, "route"), CustomerAiConsultationResult.Route.class);
        boolean degraded = requiredBoolean(node, "degraded");
        boolean handoffRequired = requiredBoolean(node, "handoffRequired");
        List<CustomerAiConsultationResult.Evidence> evidence = parseEvidence(node.get("evidence"));
        String answer = decision == CustomerAiConsultationResult.Decision.HANDOFF
                ? null
                : requiredBoundedText(node, "answer", MAX_ANSWER_LENGTH);

        validateDecision(decision, route, answer, degraded, handoffRequired, evidence);
        return new CustomerAiConsultationResult(
                requestId, decision, answer, route, degraded, handoffRequired, evidence);
    }

    private List<CustomerAiConsultationResult.Evidence> parseEvidence(JsonNode node) {
        if (!node.isArray() || node.size() > MAX_EVIDENCE) {
            throw contractError();
        }
        List<CustomerAiConsultationResult.Evidence> evidence = new ArrayList<>();
        Set<String> chunkIds = new HashSet<>();
        node.forEach(item -> {
            requireObject(item);
            assertExactFields(item, Set.of(
                    "knowledgeVersionId", "chunkId", "retrievalRank", "retrievalScore"));
            long knowledgeVersionId = requiredPositiveLong(item, "knowledgeVersionId");
            String chunkId = requiredBoundedText(item, "chunkId", MAX_CHUNK_ID_LENGTH);
            int retrievalRank = requiredPositiveInt(item, "retrievalRank");
            double retrievalScore = requiredScore(item, "retrievalScore");
            if (!chunkIds.add(chunkId)) {
                throw contractError();
            }
            evidence.add(new CustomerAiConsultationResult.Evidence(
                    knowledgeVersionId, chunkId, retrievalRank, retrievalScore));
        });
        return List.copyOf(evidence);
    }

    private void validateDecision(
            CustomerAiConsultationResult.Decision decision,
            CustomerAiConsultationResult.Route route,
            String answer,
            boolean degraded,
            boolean handoffRequired,
            List<CustomerAiConsultationResult.Evidence> evidence
    ) {
        switch (decision) {
            case ANSWER -> {
                if (answer == null || degraded || handoffRequired) {
                    throw contractError();
                }
            }
            case HANDOFF -> {
                if (answer != null || degraded || !handoffRequired || !evidence.isEmpty()) {
                    throw contractError();
                }
            }
            case DEGRADED -> {
                if (answer == null || !degraded) {
                    throw contractError();
                }
            }
        }
        if (decision != CustomerAiConsultationResult.Decision.HANDOFF
                && (route == CustomerAiConsultationResult.Route.POLICY
                || route == CustomerAiConsultationResult.Route.POLICY_AND_STATE)
                && evidence.isEmpty()) {
            throw contractError();
        }
    }

    private Set<String> withAnswer(Set<String> fields) {
        Set<String> result = new HashSet<>(fields);
        result.add("answer");
        return result;
    }

    private void assertExactFields(JsonNode node, Set<String> expected) {
        if (!new HashSet<>(node.propertyNames()).equals(expected)) {
            throw contractError();
        }
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

    private String requiredBoundedText(JsonNode node, String field, int maximumLength) {
        String value = requiredText(node, field);
        if (value.isBlank() || value.length() > maximumLength) {
            throw contractError();
        }
        return value;
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

    private double requiredScore(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw contractError();
        }
        double score = value.doubleValue();
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw contractError();
        }
        return score;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw contractError();
        }
    }

    private <E extends Enum<E>> E enumValue(String value, Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw contractError();
        }
    }

    private CustomerAiConsultationClientException contractError() {
        return new CustomerAiConsultationClientException(
                CustomerAiConsultationClientException.Reason.CONTRACT_ERROR);
    }
}
