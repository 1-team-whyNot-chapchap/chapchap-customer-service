package com.chapchap.customer.domain.knowledge.service;

import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.domain.knowledge.response.KnowledgeVersionResponse;
import com.chapchap.customer.global.error.custom.knowledge.KnowledgeVersionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeVersionQueryService {
    private final KnowledgeVersionRepository knowledgeVersionRepository;

    @Transactional(readOnly = true)
    public KnowledgeVersionResponse get(Long knowledgeVersionId) {
        return knowledgeVersionRepository.findById(knowledgeVersionId)
                .map(KnowledgeVersionResponse::from)
                .orElseThrow(KnowledgeVersionNotFoundException::new);
    }
}
