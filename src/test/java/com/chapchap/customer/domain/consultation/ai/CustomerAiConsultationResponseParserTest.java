package com.chapchap.customer.domain.consultation.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiConsultationResponseParserTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final CustomerAiConsultationResponseParser parser =
            new CustomerAiConsultationResponseParser(new ObjectMapper());

    @Test
    void parsesStrictAnswerResponse() {
        CustomerAiConsultationResult result = parser.parse(answerJson(), REQUEST_ID);

        assertThat(result.decision()).isEqualTo(CustomerAiConsultationResult.Decision.ANSWER);
        assertThat(result.route()).isEqualTo(CustomerAiConsultationResult.Route.POLICY);
        assertThat(result.answer()).isEqualTo("환불 정책의 적용 조건을 안내합니다.");
        assertThat(result.degraded()).isFalse();
        assertThat(result.requiresAdminHandoff()).isFalse();
        assertThat(result.evidence()).containsExactly(new CustomerAiConsultationResult.Evidence(
                101L, "refund-policy-v1-0001", 1, 0.91));
    }

    @Test
    void parsesStrictHandoffResponseWithoutAnswerOrEvidence() {
        String body = """
                {
                  "schemaVersion":"1.0",
                  "requestId":"11111111-1111-4111-8111-111111111111",
                  "decision":"HANDOFF",
                  "route":"USER_STATE",
                  "degraded":false,
                  "handoffRequired":true,
                  "evidence":[]
                }
                """;

        CustomerAiConsultationResult result = parser.parse(body, REQUEST_ID);

        assertThat(result.decision()).isEqualTo(CustomerAiConsultationResult.Decision.HANDOFF);
        assertThat(result.answer()).isNull();
        assertThat(result.requiresAdminHandoff()).isTrue();
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void preservesDegradedPartialAnswerAndHandoffSignal() {
        String body = """
                {
                  "schemaVersion":"1.0",
                  "requestId":"11111111-1111-4111-8111-111111111111",
                  "decision":"DEGRADED",
                  "answer":"확인된 정책만 안내합니다.",
                  "route":"POLICY_AND_STATE",
                  "degraded":true,
                  "handoffRequired":true,
                  "evidence":[{
                    "knowledgeVersionId":101,
                    "chunkId":"refund-policy-v1-0001",
                    "retrievalRank":1,
                    "retrievalScore":0.91
                  }]
                }
                """;

        CustomerAiConsultationResult result = parser.parse(body, REQUEST_ID);

        assertThat(result.decision()).isEqualTo(CustomerAiConsultationResult.Decision.DEGRADED);
        assertThat(result.degraded()).isTrue();
        assertThat(result.requiresAdminHandoff()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsUnknownFieldsContextMismatchAndInvalidDecisionContracts(String body) {
        assertThatThrownBy(() -> parser.parse(body, REQUEST_ID))
                .isExactlyInstanceOf(CustomerAiConsultationClientException.class)
                .satisfies(error -> assertThat(((CustomerAiConsultationClientException) error).reason())
                        .isEqualTo(CustomerAiConsultationClientException.Reason.CONTRACT_ERROR))
                .hasMessage("Customer-AI returned an invalid consultation contract.")
                .hasNoCause();
    }

    private static Stream<String> invalidResponses() {
        return Stream.of(
                answerJson().replace("\"evidence\":", "\"unknown\":true,\"evidence\":"),
                answerJson().replace(REQUEST_ID.toString(), "22222222-2222-4222-8222-222222222222"),
                answerJson().replace("\"degraded\":false", "\"degraded\":true"),
                answerJson().replace("\"handoffRequired\":false", "\"handoffRequired\":true"),
                answerJson().replace("\"retrievalScore\":0.91", "\"retrievalScore\":1.01"),
                answerJson().replace(
                        "{\"knowledgeVersionId\":101,\"chunkId\":\"refund-policy-v1-0001\",\"retrievalRank\":1,\"retrievalScore\":0.91}",
                        "{\"knowledgeVersionId\":101,\"chunkId\":\"refund-policy-v1-0001\",\"retrievalRank\":1,\"retrievalScore\":0.91}," +
                                "{\"knowledgeVersionId\":102,\"chunkId\":\"refund-policy-v1-0001\",\"retrievalRank\":2,\"retrievalScore\":0.80}"
                ),
                answerJson().replace(
                        "[{\"knowledgeVersionId\":101,\"chunkId\":\"refund-policy-v1-0001\",\"retrievalRank\":1,\"retrievalScore\":0.91}]",
                        "[]"
                ),
                "not-json"
        );
    }

    private static String answerJson() {
        return """
                {"schemaVersion":"1.0","requestId":"11111111-1111-4111-8111-111111111111",
                "decision":"ANSWER","answer":"환불 정책의 적용 조건을 안내합니다.","route":"POLICY",
                "degraded":false,"handoffRequired":false,
                "evidence":[{"knowledgeVersionId":101,"chunkId":"refund-policy-v1-0001","retrievalRank":1,"retrievalScore":0.91}]}
                """;
    }
}
