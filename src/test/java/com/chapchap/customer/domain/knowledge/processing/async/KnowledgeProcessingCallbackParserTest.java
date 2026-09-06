package com.chapchap.customer.domain.knowledge.processing.async;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeProcessingCallbackParserTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final KnowledgeProcessingCallbackParser parser =
            new KnowledgeProcessingCallbackParser(new ObjectMapper());
    private final KnowledgeProcessingCallbackHeaders headers =
            new KnowledgeProcessingCallbackHeaders(REQUEST_ID, 8001L);

    @Test
    void parsesStrictCompletedCallback() {
        KnowledgeProcessingCallback callback = parser.parse(completedJson(), headers);

        assertThat(callback).isEqualTo(new KnowledgeProcessingCallback(
                8001L,
                101L,
                KnowledgeProcessingCallback.Status.COMPLETED,
                3,
                "HYBRID_POLICY_V1",
                null,
                null
        ));
    }

    @Test
    void parsesStrictFailedCallback() {
        KnowledgeProcessingCallback callback = parser.parse(failedJson(), headers);

        assertThat(callback).isEqualTo(new KnowledgeProcessingCallback(
                8001L,
                101L,
                KnowledgeProcessingCallback.Status.FAILED,
                null,
                null,
                KnowledgeProcessingCallback.FailureCode.EMBEDDING_UNAVAILABLE,
                true
        ));
    }

    @Test
    void parsesAndValidatesCorrelationHeaders() {
        assertThat(KnowledgeProcessingCallbackHeaders.parse(REQUEST_ID.toString(), "8001"))
                .isEqualTo(headers);
        assertThatThrownBy(() -> KnowledgeProcessingCallbackHeaders.parse("not-uuid", "8001"))
                .isExactlyInstanceOf(KnowledgeProcessingCallbackException.class);
        assertThatThrownBy(() -> KnowledgeProcessingCallbackHeaders.parse(REQUEST_ID.toString(), "08001"))
                .isExactlyInstanceOf(KnowledgeProcessingCallbackException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidCallbacks")
    void rejectsMalformedMismatchedAndUnsupportedCallbacks(String body) {
        assertThatThrownBy(() -> parser.parse(body, headers))
                .isExactlyInstanceOf(KnowledgeProcessingCallbackException.class)
                .satisfies(error -> assertThat(((KnowledgeProcessingCallbackException) error).reason())
                        .isEqualTo(KnowledgeProcessingCallbackException.Reason.CONTRACT_ERROR))
                .hasNoCause();
    }

    private static Stream<String> invalidCallbacks() {
        return Stream.of(
                completedJson().replace("\"chunkCount\":3", "\"chunkCount\":0"),
                completedJson().replace("\"processingId\":8001", "\"processingId\":8002"),
                completedJson().replace("\"HYBRID_POLICY_V1\"", "\"OTHER\""),
                completedJson().replace("\"chunkCount\":3", "\"unknown\":true,\"chunkCount\":3"),
                completedJson().replace("\"status\":\"COMPLETED\"", "\"status\":\"FAILED\""),
                failedJson().replace("\"EMBEDDING_UNAVAILABLE\"", "\"RAW_PROVIDER_ERROR\""),
                failedJson().replace("\"retryable\":true", "\"retryable\":\"true\""),
                "not-json"
        );
    }

    private static String completedJson() {
        return """
                {"schemaVersion":"1.0","processingId":8001,"knowledgeVersionId":101,
                "status":"COMPLETED","chunkCount":3,"chunkProfile":"HYBRID_POLICY_V1"}
                """;
    }

    private static String failedJson() {
        return """
                {"schemaVersion":"1.0","processingId":8001,"knowledgeVersionId":101,
                "status":"FAILED","failureCode":"EMBEDDING_UNAVAILABLE","retryable":true}
                """;
    }
}