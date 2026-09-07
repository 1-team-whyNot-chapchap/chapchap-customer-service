package com.chapchap.customer.global.observability.customerai;

import com.chapchap.customer.domain.consultation.ai.CustomerAiConsultationResult;
import com.chapchap.customer.domain.consultation.ai.currentstate.CurrentStateCapability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CustomerAiDiagnosticEvent {
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Set<CustomerAiDiagnosticOutcome> CONSULTATION_OUTCOMES = Set.of(
            CustomerAiDiagnosticOutcome.ANSWER,
            CustomerAiDiagnosticOutcome.HANDOFF,
            CustomerAiDiagnosticOutcome.DEGRADED
    );
    private static final Set<CustomerAiDiagnosticOutcome> CALLBACK_IGNORED_OUTCOMES = Set.of(
            CustomerAiDiagnosticOutcome.IGNORED_DUPLICATE,
            CustomerAiDiagnosticOutcome.IGNORED_STALE
    );

    private final CustomerAiDiagnosticEventType eventType;
    private final UUID requestId;
    private final String traceId;
    private final Long consultationId;
    private final Long knowledgeVersionId;
    private final Long processingId;
    private final CustomerAiConsultationResult.Route route;
    private final CurrentStateCapability capabilityId;
    private final CustomerAiDiagnosticOutcome outcome;
    private final CustomerAiDiagnosticFailureCode failureCode;
    private final Boolean retryable;
    private final Long latencyMs;
    private final Integer chunkCount;

    private CustomerAiDiagnosticEvent(
            CustomerAiDiagnosticEventType eventType,
            UUID requestId,
            String traceId,
            Long consultationId,
            Long knowledgeVersionId,
            Long processingId,
            CustomerAiConsultationResult.Route route,
            CurrentStateCapability capabilityId,
            CustomerAiDiagnosticOutcome outcome,
            CustomerAiDiagnosticFailureCode failureCode,
            Boolean retryable,
            Long latencyMs,
            Integer chunkCount
    ) {
        this.eventType = Objects.requireNonNull(eventType);
        this.requestId = Objects.requireNonNull(requestId);
        if (traceId != null && !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            throw new IllegalArgumentException("traceId does not match the trusted format.");
        }
        requirePositive(consultationId, "consultationId");
        requirePositive(knowledgeVersionId, "knowledgeVersionId");
        requirePositive(processingId, "processingId");
        if (latencyMs != null && latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative.");
        }
        if (chunkCount != null && chunkCount <= 0) {
            throw new IllegalArgumentException("chunkCount must be positive.");
        }
        this.traceId = traceId;
        this.consultationId = consultationId;
        this.knowledgeVersionId = knowledgeVersionId;
        this.processingId = processingId;
        this.route = route;
        this.capabilityId = capabilityId;
        this.outcome = Objects.requireNonNull(outcome);
        this.failureCode = failureCode;
        this.retryable = retryable;
        this.latencyMs = latencyMs;
        this.chunkCount = chunkCount;
    }

    public static CustomerAiDiagnosticEvent consultationResult(
            UUID requestId,
            String traceId,
            long consultationId,
            CustomerAiConsultationResult.Route route,
            CustomerAiDiagnosticOutcome outcome
    ) {
        if (!CONSULTATION_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Consultation outcome is invalid.");
        }
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.CONSULTATION_RESULT,
                requestId, traceId, consultationId, null, null, Objects.requireNonNull(route), null,
                outcome, null, null, null, null
        );
    }

    public static CustomerAiDiagnosticEvent consultationFailure(
            UUID requestId,
            String traceId,
            long consultationId,
            CustomerAiDiagnosticFailureCode failureCode,
            boolean retryable
    ) {
        return failure(
                CustomerAiDiagnosticEventType.CONSULTATION_FAILED,
                requestId, traceId, consultationId, null, null, failureCode, retryable
        );
    }

    public static CustomerAiDiagnosticEvent knowledgeSubmitted(
            UUID requestId,
            String traceId,
            long knowledgeVersionId,
            long processingId
    ) {
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.KNOWLEDGE_SUBMITTED,
                requestId, traceId, null, knowledgeVersionId, processingId, null, null,
                CustomerAiDiagnosticOutcome.ACCEPTED, null, null, null, null
        );
    }

    public static CustomerAiDiagnosticEvent knowledgeSubmissionFailure(
            UUID requestId,
            String traceId,
            long knowledgeVersionId,
            CustomerAiDiagnosticFailureCode failureCode,
            boolean retryable
    ) {
        return failure(
                CustomerAiDiagnosticEventType.KNOWLEDGE_SUBMISSION_FAILED,
                requestId, traceId, null, knowledgeVersionId, null, failureCode, retryable
        );
    }

    public static CustomerAiDiagnosticEvent knowledgeCallbackCompleted(
            UUID requestId,
            String traceId,
            long knowledgeVersionId,
            long processingId,
            int chunkCount
    ) {
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.KNOWLEDGE_CALLBACK_RESULT,
                requestId, traceId, null, knowledgeVersionId, processingId, null, null,
                CustomerAiDiagnosticOutcome.COMPLETED, null, null, null, chunkCount
        );
    }

    public static CustomerAiDiagnosticEvent knowledgeCallbackFailed(
            UUID requestId,
            String traceId,
            long knowledgeVersionId,
            long processingId,
            CustomerAiDiagnosticFailureCode failureCode,
            boolean retryable
    ) {
        return failure(
                CustomerAiDiagnosticEventType.KNOWLEDGE_CALLBACK_RESULT,
                requestId, traceId, null, knowledgeVersionId, processingId, failureCode, retryable
        );
    }

    public static CustomerAiDiagnosticEvent knowledgeCallbackIgnored(
            UUID requestId,
            String traceId,
            long knowledgeVersionId,
            long processingId,
            CustomerAiDiagnosticOutcome outcome
    ) {
        if (!CALLBACK_IGNORED_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Ignored callback outcome is invalid.");
        }
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.KNOWLEDGE_CALLBACK_RESULT,
                requestId, traceId, null, knowledgeVersionId, processingId, null, null,
                outcome, null, null, null, null
        );
    }

    public static CustomerAiDiagnosticEvent knowledgeCallbackRejected(
            UUID requestId,
            String traceId,
            long processingId,
            CustomerAiDiagnosticFailureCode failureCode
    ) {
        return failure(
                CustomerAiDiagnosticEventType.KNOWLEDGE_CALLBACK_RESULT,
                requestId, traceId, null, null, processingId, failureCode, false
        );
    }

    public static CustomerAiDiagnosticEvent summarySubmitted(
            UUID requestId,
            String traceId,
            long consultationId
    ) {
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.SUMMARY_SUBMITTED,
                requestId, traceId, consultationId, null, null, null, null,
                CustomerAiDiagnosticOutcome.ACCEPTED, null, null, null, null
        );
    }

    public static CustomerAiDiagnosticEvent summarySubmissionFailure(
            UUID requestId,
            String traceId,
            long consultationId,
            CustomerAiDiagnosticFailureCode failureCode,
            boolean retryable
    ) {
        return failure(
                CustomerAiDiagnosticEventType.SUMMARY_SUBMISSION_FAILED,
                requestId, traceId, consultationId, null, null, failureCode, retryable
        );
    }

    public static CustomerAiDiagnosticEvent summaryCallbackCompleted(
            UUID requestId,
            String traceId,
            long consultationId
    ) {
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.SUMMARY_CALLBACK_RESULT,
                requestId, traceId, consultationId, null, null, null, null,
                CustomerAiDiagnosticOutcome.COMPLETED, null, null, null, null
        );
    }

    public static CustomerAiDiagnosticEvent summaryCallbackFailed(
            UUID requestId,
            String traceId,
            long consultationId,
            CustomerAiDiagnosticFailureCode failureCode,
            boolean retryable
    ) {
        return failure(
                CustomerAiDiagnosticEventType.SUMMARY_CALLBACK_RESULT,
                requestId, traceId, consultationId, null, null, failureCode, retryable
        );
    }

    public static CustomerAiDiagnosticEvent summaryCallbackIgnored(
            UUID requestId,
            String traceId,
            long consultationId,
            CustomerAiDiagnosticOutcome outcome
    ) {
        if (!CALLBACK_IGNORED_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("Ignored callback outcome is invalid.");
        }
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.SUMMARY_CALLBACK_RESULT,
                requestId, traceId, consultationId, null, null, null, null,
                outcome, null, null, null, null
        );
    }

    public static CustomerAiDiagnosticEvent summaryCallbackRejected(
            UUID requestId,
            String traceId,
            CustomerAiDiagnosticFailureCode failureCode
    ) {
        return failure(
                CustomerAiDiagnosticEventType.SUMMARY_CALLBACK_RESULT,
                requestId, traceId, null, null, null, failureCode, false
        );
    }

    public static CustomerAiDiagnosticEvent currentStateAccessGranted(
            UUID requestId,
            String traceId,
            long consultationId,
            CurrentStateCapability capabilityId
    ) {
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.CURRENT_STATE_ACCESS_GRANTED,
                requestId, traceId, consultationId, null, null, null, Objects.requireNonNull(capabilityId),
                CustomerAiDiagnosticOutcome.ACCESS_GRANTED, null, null, null, null
        );
    }

    public static CustomerAiDiagnosticEvent currentStateAccessDenied(
            UUID requestId,
            String traceId,
            long consultationId,
            CurrentStateCapability capabilityId,
            CustomerAiDiagnosticFailureCode failureCode
    ) {
        return new CustomerAiDiagnosticEvent(
                CustomerAiDiagnosticEventType.CURRENT_STATE_ACCESS_DENIED,
                requestId, traceId, consultationId, null, null, null, Objects.requireNonNull(capabilityId),
                CustomerAiDiagnosticOutcome.ACCESS_DENIED, Objects.requireNonNull(failureCode), false, null, null
        );
    }

    private static CustomerAiDiagnosticEvent failure(
            CustomerAiDiagnosticEventType eventType,
            UUID requestId,
            String traceId,
            Long consultationId,
            Long knowledgeVersionId,
            Long processingId,
            CustomerAiDiagnosticFailureCode failureCode,
            boolean retryable
    ) {
        return new CustomerAiDiagnosticEvent(
                eventType,
                requestId, traceId, consultationId, knowledgeVersionId, processingId, null, null,
                CustomerAiDiagnosticOutcome.FAILED, Objects.requireNonNull(failureCode), retryable, null, null
        );
    }

    private static void requirePositive(Long value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
    }

    public CustomerAiDiagnosticEventType eventType() {
        return eventType;
    }

    public UUID requestId() {
        return requestId;
    }

    public String traceId() {
        return traceId;
    }

    public Long consultationId() {
        return consultationId;
    }

    public Long knowledgeVersionId() {
        return knowledgeVersionId;
    }

    public Long processingId() {
        return processingId;
    }

    public CustomerAiConsultationResult.Route route() {
        return route;
    }

    public CurrentStateCapability capabilityId() {
        return capabilityId;
    }

    public CustomerAiDiagnosticOutcome outcome() {
        return outcome;
    }

    public CustomerAiDiagnosticFailureCode failureCode() {
        return failureCode;
    }

    public Boolean retryable() {
        return retryable;
    }

    public Long latencyMs() {
        return latencyMs;
    }

    public Integer chunkCount() {
        return chunkCount;
    }

    public Map<String, String> metricDimensions() {
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("eventType", eventType.name());
        dimensions.put("outcome", outcome.name());
        if (route != null) {
            dimensions.put("route", route.name());
        }
        if (capabilityId != null) {
            dimensions.put("capabilityId", capabilityId.id());
        }
        if (failureCode != null) {
            dimensions.put("failureCode", failureCode.name());
        }
        if (retryable != null) {
            dimensions.put("retryable", retryable.toString());
        }
        return Map.copyOf(dimensions);
    }
}
