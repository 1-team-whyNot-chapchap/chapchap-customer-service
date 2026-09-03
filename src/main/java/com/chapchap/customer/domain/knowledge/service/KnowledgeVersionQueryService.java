package com.chapchap.customer.domain.knowledge.service;

import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.domain.knowledge.response.KnowledgeVersionResponse;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
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
                .orElseThrow(() -> new BusinessException(
                        CustomResponseCode.NOT_FOUND_RESOURCE_ERROR,
                        "Knowledge Version을 찾을 수 없습니다."
                ));
    }
}
