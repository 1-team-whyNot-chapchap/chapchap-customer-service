package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallbackHeaders;
import com.chapchap.customer.global.exception.consultation.summary.ConsultationSummaryCallbackException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ConsultationSummaryCallbackParser {
    private static final Set<String> COMMON_FIELDS = Set.of(
            "schemaVersion", "summaryJobId", "consultationId", "status"
    );
    private static final int MAX_SUMMARY_LENGTH = 10_000;
    private final ObjectMapper objectMapper;

    public ConsultationSummaryCallbackParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public ConsultationSummaryCallback parse(String body, ConsultationSummaryCallbackHeaders headers) {
        if (body == null || body.isBlank() || headers == null) {
            throw contractError();
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            requireObject(node);
            ConsultationSummaryCallback.Status status = enumValue(
                    requiredText(node, "status"), ConsultationSummaryCallback.Status.class);
            Set<String> expected = new HashSet<>(COMMON_FIELDS);
            if (status == ConsultationSummaryCallback.Status.COMPLETED) {
                expected.add("summary");
            } else {
                expected.add("failureCode");
                expected.add("retryable");
            }
            if (!new HashSet<>(node.propertyNames()).equals(expected)
                    || !"1.0".equals(requiredText(node, "schemaVersion"))) {
                throw contractError();
            }
            long summaryJobId = requiredPositiveLong(node, "summaryJobId");
            long consultationId = requiredPositiveLong(node, "consultationId");
            if (summaryJobId != headers.idempotencySummaryJobId()) {
                throw contractError();
            }
            if (status == ConsultationSummaryCallback.Status.COMPLETED) {
                String summary = requiredBoundedText(node, "summary", MAX_SUMMARY_LENGTH);
                return new ConsultationSummaryCallback(
                        summaryJobId, consultationId, status, summary, null, null);
            }
            ConsultationSummaryCallback.FailureCode failureCode = enumValue(
                    requiredText(node, "failureCode"), ConsultationSummaryCallback.FailureCode.class);
            boolean retryable = requiredBoolean(node, "retryable");
            return new ConsultationSummaryCallback(
                    summaryJobId, consultationId, status, null, failureCode, retryable);
        } catch (ConsultationSummaryCallbackException exception) {
            throw exception;
        } catch (Exception exception) {
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

    private <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw contractError();
        }
    }

    private ConsultationSummaryCallbackException contractError() {
        return new ConsultationSummaryCallbackException(
                ConsultationSummaryCallbackException.Reason.CONTRACT_ERROR);
    }
}