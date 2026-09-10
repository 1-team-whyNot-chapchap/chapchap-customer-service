package com.chapchap.customer.domain.knowledge.service.processing;

import com.chapchap.customer.domain.knowledge.dto.processing.KnowledgeProcessingResult;
import com.chapchap.customer.domain.knowledge.request.processing.KnowledgeProcessingRequest;

public interface KnowledgeProcessingClient {
    KnowledgeProcessingResult process(KnowledgeProcessingRequest request);
}
