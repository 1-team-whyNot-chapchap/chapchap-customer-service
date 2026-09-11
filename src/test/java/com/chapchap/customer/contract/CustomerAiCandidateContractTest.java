package com.chapchap.customer.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerAiCandidateContractTest {

    private static JsonNode fixture;

    @BeforeAll
    static void loadFixture() throws Exception {
        try (InputStream input = CustomerAiCandidateContractTest.class.getResourceAsStream(
                "/contracts/customer-ai-candidate-v1.json")) {
            assertThat(input).as("candidate fixture must exist").isNotNull();
            fixture = new ObjectMapper().readTree(input);
        }
    }

    @Test
    void declaresCandidateV1RootContract() {
        assertExactFields(fixture, "contractStatus", "schemaVersion", "consultation", "knowledge", "summary", "currentState");
        assertThat(fixture.path("contractStatus").textValue()).isEqualTo("CANDIDATE");
        assertThat(fixture.path("schemaVersion").textValue()).isEqualTo("1.0");
    }

    @Test
    void validatesConsultationRequestAndAllDecisionShapes() {
        JsonNode consultation = fixture.required("consultation");
        assertExactFields(consultation, "request", "answer", "handoff", "degraded");

        JsonNode request = consultation.required("request");
        assertExactFields(request, "schemaVersion", "requestId", "consultationId", "triggerMessageId", "subject", "message", "conversationContext");
        assertSchemaVersion(request);
        assertUuid(request, "requestId");
        assertPositiveLong(request, "consultationId");
        assertPositiveLong(request, "triggerMessageId");
        assertThat(request.required("message").textValue()).isNotBlank();
        assertThat(request.required("conversationContext").isArray()).isTrue();

        JsonNode subject = request.required("subject");
        assertExactFields(subject, "userId", "role", "allowedAiScopes");
        assertPositiveLong(subject, "userId");
        assertEnum(subject, "role", "CUSTOMER", "ADMIN", "SUPER_ADMIN");
        assertThat(subject.required("allowedAiScopes").isArray()).isTrue();
        assertThat(subject.required("allowedAiScopes")).allSatisfy(scope -> assertThat(scope.textValue()).isNotBlank());

        assertAnswer(consultation.required("answer"));
        assertHandoff(consultation.required("handoff"));
        assertDegraded(consultation.required("degraded"));
    }

    @Test
    void validatesKnowledgeAsynchronousJobShapes() {
        JsonNode knowledge = fixture.required("knowledge");
        assertExactFields(knowledge, "request", "accepted", "completed", "failed");

        JsonNode request = knowledge.required("request");
        assertExactFields(request, "schemaVersion", "knowledgeVersionId", "attempt", "source", "metadata", "chunkProfile", "callback");
        assertSchemaVersion(request);
        assertPositiveLong(request, "knowledgeVersionId");
        assertPositiveLong(request, "attempt");
        assertThat(request.required("chunkProfile").textValue()).isEqualTo("HYBRID_POLICY_V1");

        JsonNode source = request.required("source");
        assertExactFields(source, "downloadUrl", "contentType", "fileSize");
        assertHttpsUri(source, "downloadUrl");
        assertThat(source.required("contentType").textValue()).isNotBlank();
        assertPositiveLong(source, "fileSize");

        JsonNode metadata = request.required("metadata");
        assertExactFields(metadata, "documentKey", "sourceService", "category", "version", "effectiveFrom");
        assertNonBlankText(metadata, "documentKey", "sourceService", "category", "version");
        OffsetDateTime.parse(metadata.required("effectiveFrom").textValue());
        assertCallback(request.required("callback"), "/internal/v1/knowledge-processing-results");

        JsonNode accepted = knowledge.required("accepted");
        assertExactFields(accepted, "schemaVersion", "processingId", "knowledgeVersionId", "status");
        assertJobIdentity(accepted, "processingId", "knowledgeVersionId");
        assertEnum(accepted, "status", "ACCEPTED");

        JsonNode completed = knowledge.required("completed");
        assertExactFields(completed, "schemaVersion", "processingId", "knowledgeVersionId", "status", "chunkCount", "chunkProfile");
        assertJobIdentity(completed, "processingId", "knowledgeVersionId");
        assertEnum(completed, "status", "COMPLETED");
        assertPositiveLong(completed, "chunkCount");
        assertThat(completed.required("chunkProfile").textValue()).isEqualTo("HYBRID_POLICY_V1");
        assertThat(completed.has("failureCode")).isFalse();
        assertThat(completed.has("retryable")).isFalse();

        JsonNode failed = knowledge.required("failed");
        assertExactFields(failed, "schemaVersion", "processingId", "knowledgeVersionId", "status", "failureCode", "retryable");
        assertJobIdentity(failed, "processingId", "knowledgeVersionId");
        assertEnum(failed, "status", "FAILED");
        assertEnum(failed, "failureCode", "SOURCE_FETCH_FAILED", "TEXT_EXTRACTION_FAILED", "UNSUPPORTED_DOCUMENT",
                "ENCRYPTED_DOCUMENT", "CHUNK_PROFILE_INVALID", "EMBEDDING_UNAVAILABLE",
                "VECTOR_STORE_UNAVAILABLE", "PROCESSING_TIMEOUT", "CUSTOMER_AI_UNAVAILABLE");
        assertThat(failed.required("retryable").isBoolean()).isTrue();
        assertThat(failed.has("chunkCount")).isFalse();
    }

    @Test
    void validatesSummaryAsynchronousJobShapes() {
        JsonNode summary = fixture.required("summary");
        assertExactFields(summary, "request", "accepted", "completed", "failed");

        JsonNode request = summary.required("request");
        assertExactFields(request, "schemaVersion", "summaryJobId", "consultationId", "messages", "callback");
        assertSchemaVersion(request);
        assertPositiveLong(request, "summaryJobId");
        assertPositiveLong(request, "consultationId");
        assertThat(request.required("messages").isArray()).isTrue();
        assertThat(request.required("messages")).isNotEmpty().allSatisfy(message -> {
            assertExactFields(message, "senderType", "content");
            assertEnum(message, "senderType", "USER", "ADMIN", "AI");
            assertThat(message.required("content").textValue()).isNotBlank();
        });
        assertCallback(request.required("callback"), "/internal/v1/consultation-summary-results");

        JsonNode accepted = summary.required("accepted");
        assertSummaryIdentity(accepted, "schemaVersion", "summaryJobId", "consultationId", "status");
        assertEnum(accepted, "status", "ACCEPTED");

        JsonNode completed = summary.required("completed");
        assertSummaryIdentity(completed, "schemaVersion", "summaryJobId", "consultationId", "status", "summary");
        assertEnum(completed, "status", "COMPLETED");
        assertThat(completed.required("summary").textValue()).isNotBlank();
        assertThat(completed.has("failureCode")).isFalse();
        assertThat(completed.has("retryable")).isFalse();

        JsonNode failed = summary.required("failed");
        assertSummaryIdentity(failed, "schemaVersion", "summaryJobId", "consultationId", "status", "failureCode", "retryable");
        assertEnum(failed, "status", "FAILED");
        assertEnum(failed, "failureCode", "UNSAFE_CONTEXT", "SUMMARY_GENERATION_FAILED",
                "LLM_UNAVAILABLE", "PROCESSING_TIMEOUT", "CUSTOMER_AI_UNAVAILABLE");
        assertThat(failed.required("retryable").isBoolean()).isTrue();
        assertThat(failed.has("summary")).isFalse();
    }

    @Test
    void validatesCurrentStateDomainShapes() {
        JsonNode state = fixture.required("currentState");
        assertExactFields(state, "payment", "refund", "subscription", "delivery");

        JsonNode payment = state.required("payment");
        assertExactFields(payment, "availability", "status", "paymentType", "amount", "occurredAt");
        assertAvailability(payment);
        assertNonBlankText(payment, "status", "paymentType");
        assertPositiveLong(payment, "amount");
        OffsetDateTime.parse(payment.required("occurredAt").textValue());

        JsonNode refund = state.required("refund");
        assertExactFields(refund, "availability", "status", "refundType", "requestedAmount", "refundedAmount", "unprocessedAmount", "requestedAt", "completedAt");
        assertAvailability(refund);
        assertNonBlankText(refund, "status", "refundType");
        assertPositiveLong(refund, "requestedAmount");
        assertNonNegativeLong(refund, "refundedAmount");
        assertNonNegativeLong(refund, "unprocessedAmount");
        assertThat(refund.required("refundedAmount").longValue() + refund.required("unprocessedAmount").longValue())
                .isEqualTo(refund.required("requestedAmount").longValue());
        OffsetDateTime.parse(refund.required("requestedAt").textValue());
        assertThat(refund.required("completedAt").isNull()).isTrue();

        JsonNode subscription = state.required("subscription");
        assertExactFields(subscription, "availability", "status");
        assertAvailability(subscription);
        assertNonBlankText(subscription, "status");

        JsonNode delivery = state.required("delivery");
        assertExactFields(delivery, "availability", "status", "delayStatus", "statusChangedAt");
        assertAvailability(delivery);
        assertNonBlankText(delivery, "status", "delayStatus");
        assertThat(delivery.required("statusChangedAt").isNull()).isTrue();
    }

    private static void assertAnswer(JsonNode response) {
        assertExactFields(response, "schemaVersion", "requestId", "decision", "answer", "route", "degraded", "handoffRequired", "evidence");
        assertCommonConsultationResponse(response, "ANSWER", "POLICY", false, false);
        assertThat(response.required("answer").textValue()).isNotBlank();
        assertEvidence(response.required("evidence"), false);
    }

    private static void assertHandoff(JsonNode response) {
        assertExactFields(response, "schemaVersion", "requestId", "decision", "route", "degraded", "handoffRequired", "evidence");
        assertCommonConsultationResponse(response, "HANDOFF", "USER_STATE", false, true);
        assertThat(response.has("answer")).isFalse();
        assertEvidence(response.required("evidence"), true);
    }

    private static void assertDegraded(JsonNode response) {
        assertExactFields(response, "schemaVersion", "requestId", "decision", "answer", "route", "degraded", "handoffRequired", "evidence");
        assertCommonConsultationResponse(response, "DEGRADED", "POLICY_AND_STATE", true, true);
        assertThat(response.required("answer").textValue()).isNotBlank();
        assertEvidence(response.required("evidence"), false);
    }

    private static void assertCommonConsultationResponse(JsonNode response, String decision, String route,
                                                          boolean degraded, boolean handoffRequired) {
        assertSchemaVersion(response);
        assertUuid(response, "requestId");
        assertEnum(response, "decision", decision);
        assertEnum(response, "route", route);
        assertThat(response.required("degraded").booleanValue()).isEqualTo(degraded);
        assertThat(response.required("handoffRequired").booleanValue()).isEqualTo(handoffRequired);
    }

    private static void assertEvidence(JsonNode evidence, boolean empty) {
        assertThat(evidence.isArray()).isTrue();
        assertThat(evidence.isEmpty()).isEqualTo(empty);
        evidence.forEach(item -> {
            assertExactFields(item, "knowledgeVersionId", "chunkId", "retrievalRank", "retrievalScore");
            assertPositiveLong(item, "knowledgeVersionId");
            assertPositiveLong(item, "retrievalRank");
            assertThat(item.required("chunkId").textValue()).isNotBlank();
            assertThat(item.required("retrievalScore").doubleValue()).isBetween(0.0, 1.0);
        });
    }

    private static void assertJobIdentity(JsonNode node, String firstId, String secondId) {
        assertSchemaVersion(node);
        assertPositiveLong(node, firstId);
        assertPositiveLong(node, secondId);
    }

    private static void assertSummaryIdentity(JsonNode node, String... fields) {
        assertExactFields(node, fields);
        assertSchemaVersion(node);
        assertPositiveLong(node, "summaryJobId");
        assertPositiveLong(node, "consultationId");
    }

    private static void assertCallback(JsonNode callback, String path) {
        assertExactFields(callback, "resultUri");
        URI uri = URI.create(callback.required("resultUri").textValue());
        assertThat(uri.isAbsolute()).isFalse();
        assertThat(uri.getPath()).isEqualTo(path);
    }

    private static void assertHttpsUri(JsonNode node, String field) {
        URI uri = URI.create(node.required(field).textValue());
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isNotBlank();
    }

    private static void assertSchemaVersion(JsonNode node) {
        assertThat(node.required("schemaVersion").textValue()).isEqualTo("1.0");
    }

    private static void assertUuid(JsonNode node, String field) {
        UUID.fromString(node.required(field).textValue());
    }

    private static void assertAvailability(JsonNode node) {
        assertEnum(node, "availability", "AVAILABLE", "NOT_FOUND", "UNAVAILABLE", "TIMEOUT", "FORBIDDEN");
    }

    private static void assertPositiveLong(JsonNode node, String field) {
        JsonNode value = node.required(field);
        assertThat(value.isIntegralNumber()).isTrue();
        assertThat(value.canConvertToLong()).isTrue();
        assertThat(value.longValue()).isPositive();
    }

    private static void assertNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.required(field);
        assertThat(value.isIntegralNumber()).isTrue();
        assertThat(value.canConvertToLong()).isTrue();
        assertThat(value.longValue()).isNotNegative();
    }

    private static void assertNonBlankText(JsonNode node, String... fields) {
        for (String field : fields) {
            assertThat(node.required(field).isTextual()).isTrue();
            assertThat(node.required(field).textValue()).isNotBlank();
        }
    }

    private static void assertEnum(JsonNode node, String field, String... allowed) {
        assertThat(node.required(field).isTextual()).isTrue();
        assertThat(node.required(field).textValue()).isIn((Object[]) allowed);
    }

    private static void assertExactFields(JsonNode node, String... expected) {
        assertThat(node.isObject()).isTrue();
        Set<String> actual = new HashSet<>();
        actual.addAll(node.propertyNames());
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }
}
