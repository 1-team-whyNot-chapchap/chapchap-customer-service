package com.chapchap.customer.domain.knowledge.processing.async;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiKnowledgeJobResponseParserTest {
    private final CustomerAiKnowledgeJobResponseParser parser =
            new CustomerAiKnowledgeJobResponseParser(new ObjectMapper());

    @ParameterizedTest
    @MethodSource("invalidAcceptedResponses")
    void rejectsMalformedOrMismatchedAcceptedResponse(String body) {
        assertThatThrownBy(() -> parser.parse(body, 101L))
                .isExactlyInstanceOf(CustomerAiKnowledgeJobClientException.class)
                .satisfies(error -> assertThat(((CustomerAiKnowledgeJobClientException) error).reason())
                        .isEqualTo(CustomerAiKnowledgeJobClientException.Reason.CONTRACT_ERROR))
                .hasNoCause();
    }

    private static Stream<String> invalidAcceptedResponses() {
        String valid = """
                {"schemaVersion":"1.0","processingId":8001,"knowledgeVersionId":101,"status":"ACCEPTED"}
                """;
        return Stream.of(
                valid.replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":\"2.0\""),
                valid.replace("\"processingId\":8001", "\"processingId\":0"),
                valid.replace("\"knowledgeVersionId\":101", "\"knowledgeVersionId\":102"),
                valid.replace("\"status\":\"ACCEPTED\"", "\"status\":\"COMPLETED\""),
                valid.replace("\"status\":", "\"unknown\":true,\"status\":"),
                "not-json"
        );
    }
}