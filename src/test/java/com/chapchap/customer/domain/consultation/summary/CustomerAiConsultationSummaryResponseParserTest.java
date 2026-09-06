package com.chapchap.customer.domain.consultation.summary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiConsultationSummaryResponseParserTest {
    private final CustomerAiConsultationSummaryResponseParser parser =
            new CustomerAiConsultationSummaryResponseParser(new ObjectMapper());

    @Test
    void parsesStrictAcceptedResponse() {
        assertThat(parser.parse(valid(), 7001L, 501L))
                .isEqualTo(new CustomerAiConsultationSummaryAccepted(7001L, 501L));
    }

    @ParameterizedTest
    @MethodSource("invalidAcceptedResponses")
    void rejectsMalformedOrMismatchedAcceptedResponse(String body) {
        assertThatThrownBy(() -> parser.parse(body, 7001L, 501L))
                .isExactlyInstanceOf(CustomerAiConsultationSummaryClientException.class)
                .satisfies(error -> assertThat(((CustomerAiConsultationSummaryClientException) error).reason())
                        .isEqualTo(CustomerAiConsultationSummaryClientException.Reason.CONTRACT_ERROR))
                .hasNoCause();
    }

    private static Stream<String> invalidAcceptedResponses() {
        return Stream.of(
                valid().replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":\"2.0\""),
                valid().replace("\"summaryJobId\":7001", "\"summaryJobId\":7002"),
                valid().replace("\"consultationId\":501", "\"consultationId\":502"),
                valid().replace("\"status\":\"ACCEPTED\"", "\"status\":\"COMPLETED\""),
                valid().replace("\"status\":", "\"unknown\":true,\"status\":"),
                "not-json"
        );
    }

    private static String valid() {
        return """
                {"schemaVersion":"1.0","summaryJobId":7001,"consultationId":501,"status":"ACCEPTED"}
                """;
    }
}