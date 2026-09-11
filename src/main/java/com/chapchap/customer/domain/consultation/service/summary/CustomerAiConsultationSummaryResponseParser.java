package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.global.exception.consultation.summary.CustomerAiConsultationSummaryClientException;
import com.chapchap.customer.domain.consultation.response.summary.CustomerAiConsultationSummaryAccepted;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CustomerAiConsultationSummaryResponseParser {
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "summaryJobId", "consultationId", "status"
    );
    private final ObjectMapper objectMapper;

    public CustomerAiConsultationSummaryResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public CustomerAiConsultationSummaryAccepted parse(
            String body,
            long expectedSummaryJobId,
            long expectedConsultationId
    ) {
        if (body == null || body.isBlank() || expectedSummaryJobId <= 0 || expectedConsultationId <= 0) {
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
            long summaryJobId = requiredPositiveLong(node, "summaryJobId");
            long consultationId = requiredPositiveLong(node, "consultationId");
            if (summaryJobId != expectedSummaryJobId || consultationId != expectedConsultationId) {
                throw contractError();
            }
            return new CustomerAiConsultationSummaryAccepted(summaryJobId, consultationId);
        } catch (CustomerAiConsultationSummaryClientException exception) {
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

    private CustomerAiConsultationSummaryClientException contractError() {
        return new CustomerAiConsultationSummaryClientException(
                CustomerAiConsultationSummaryClientException.Reason.CONTRACT_ERROR);
    }
}