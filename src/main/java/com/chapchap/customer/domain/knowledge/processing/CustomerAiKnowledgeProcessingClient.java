package com.chapchap.customer.domain.knowledge.processing;

import com.chapchap.customer.global.config.CustomerAiKnowledgeProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class CustomerAiKnowledgeProcessingClient implements KnowledgeProcessingClient {
    private static final Set<String> FAILURE_CODES = Set.of(
            "SOURCE_FETCH_FAILED",
            "TEXT_EXTRACTION_FAILED",
            "UNSUPPORTED_DOCUMENT",
            "ENCRYPTED_DOCUMENT",
            "CHUNK_PROFILE_INVALID",
            "EMBEDDING_UNAVAILABLE",
            "VECTOR_STORE_UNAVAILABLE",
            "PROCESSING_TIMEOUT"
    );

    private final RestClient customerAiKnowledgeProcessingRestClient;
    private final CustomerAiKnowledgeProcessingProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public KnowledgeProcessingResult process(KnowledgeProcessingRequest request) {
        if (properties.getServiceToken().isBlank()) {
            throw new KnowledgeProcessingContractException("Customer-AI 서비스 계정 토큰이 설정되지 않았습니다.");
        }

        try {
            KnowledgeProcessingResponse response = customerAiKnowledgeProcessingRestClient.post()
                    .uri("/internal/v1/knowledge-processing")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(KnowledgeProcessingResponse.class);
            return toResult(request.knowledgeVersionId(), response);
        } catch (RestClientResponseException exception) {
            return toResult(request.knowledgeVersionId(), readFailureResponse(exception));
        }
    }

    private KnowledgeProcessingResponse readFailureResponse(RestClientResponseException exception) {
        try {
            return objectMapper.readValue(exception.getResponseBodyAsString(), KnowledgeProcessingResponse.class);
        } catch (Exception parsingException) {
            throw new KnowledgeProcessingContractException("Customer-AI 처리 실패 응답 계약이 올바르지 않습니다.", parsingException);
        }
    }

    private KnowledgeProcessingResult toResult(Long expectedVersionId, KnowledgeProcessingResponse response) {
        if (response == null || !expectedVersionId.equals(response.knowledgeVersionId())) {
            throw new KnowledgeProcessingContractException("Customer-AI 처리 결과의 Knowledge Version ID가 일치하지 않습니다.");
        }

        if ("COMPLETED".equals(response.status())
                && response.chunkCount() != null
                && response.chunkCount() > 0
                && response.failureCode() == null
                && response.retryable() == null) {
            return KnowledgeProcessingResult.completed(response.chunkCount());
        }

        if ("FAILED".equals(response.status())
                && FAILURE_CODES.contains(response.failureCode())
                && response.retryable() != null) {
            return KnowledgeProcessingResult.failed(response.failureCode(), response.retryable());
        }

        throw new KnowledgeProcessingContractException("Customer-AI 처리 결과 계약이 올바르지 않습니다.");
    }
}
