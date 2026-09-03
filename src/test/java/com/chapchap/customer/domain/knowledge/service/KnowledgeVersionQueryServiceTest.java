package com.chapchap.customer.domain.knowledge.service;

import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.global.error.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeVersionQueryServiceTest {
    @Mock
    private KnowledgeVersionRepository knowledgeVersionRepository;

    @InjectMocks
    private KnowledgeVersionQueryService knowledgeVersionQueryService;

    @Test
    void rejectsStatusLookupForUnknownKnowledgeVersion() {
        when(knowledgeVersionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeVersionQueryService.get(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Knowledge Version을 찾을 수 없습니다.");
    }
}
