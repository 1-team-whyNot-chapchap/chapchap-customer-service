package com.chapchap.customer.domain.consultation.summary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultationSummaryCallbackParserTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final ConsultationSummaryCallbackParser parser =
            new ConsultationSummaryCallbackParser(new ObjectMapper());
    private final ConsultationSummaryCallbackHeaders headers =
            new ConsultationSummaryCallbackHeaders(REQUEST_ID, 7001L);

    @Test
    void parsesStrictCompletedCallback() {
        assertThat(parser.parse(completedJson(), headers)).isEqualTo(new ConsultationSummaryCallback(
                7001L,
                501L,
                ConsultationSummaryCallback.Status.COMPLETED,
                "사용자가 환불을 문의했고 정책을 안내받았습니다.",
                null,
                null
        ));
    }

    @Test
    void parsesStrictFailedCallback() {
        assertThat(parser.parse(failedJson(), headers)).isEqualTo(new ConsultationSummaryCallback(
                7001L,
                501L,
                ConsultationSummaryCallback.Status.FAILED,
                null,
                ConsultationSummaryCallback.FailureCode.LLM_UNAVAILABLE,
                true
        ));
    }

    @Test
    void parsesAndValidatesCorrelationHeaders() {
        assertThat(ConsultationSummaryCallbackHeaders.parse(REQUEST_ID.toString(), "7001"))
                .isEqualTo(headers);
        assertThatThrownBy(() -> ConsultationSummaryCallbackHeaders.parse("not-uuid", "7001"))
                .isExactlyInstanceOf(ConsultationSummaryCallbackException.class);
        assertThatThrownBy(() -> ConsultationSummaryCallbackHeaders.parse(REQUEST_ID.toString(), "07001"))
                .isExactlyInstanceOf(ConsultationSummaryCallbackException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidCallbacks")
    void rejectsMalformedMismatchedAndUnsupportedCallbacks(String body) {
        assertThatThrownBy(() -> parser.parse(body, headers))
                .isExactlyInstanceOf(ConsultationSummaryCallbackException.class)
                .satisfies(error -> assertThat(((ConsultationSummaryCallbackException) error).reason())
                        .isEqualTo(ConsultationSummaryCallbackException.Reason.CONTRACT_ERROR))
                .hasMessageNotContaining("provider-private")
                .hasNoCause();
    }

    private static Stream<String> invalidCallbacks() {
        return Stream.of(
                completedJson().replace("\"summaryJobId\":7001", "\"summaryJobId\":7002"),
                completedJson().replace("\"summary\":\"사용자가 환불을 문의했고 정책을 안내받았습니다.\"", "\"summary\":\"   \""),
                completedJson().replace("\"summary\":", "\"unknown\":true,\"summary\":"),
                completedJson().replace("\"status\":\"COMPLETED\"", "\"status\":\"FAILED\""),
                failedJson().replace("\"LLM_UNAVAILABLE\"", "\"PROVIDER_PRIVATE\""),
                failedJson().replace("\"retryable\":true", "\"retryable\":\"true\""),
                "provider-private not-json"
        );
    }

    private static String completedJson() {
        return """
                {"schemaVersion":"1.0","summaryJobId":7001,"consultationId":501,
                "status":"COMPLETED","summary":"사용자가 환불을 문의했고 정책을 안내받았습니다."}
                """;
    }

    private static String failedJson() {
        return """
                {"schemaVersion":"1.0","summaryJobId":7001,"consultationId":501,
                "status":"FAILED","failureCode":"LLM_UNAVAILABLE","retryable":true}
                """;
    }
}