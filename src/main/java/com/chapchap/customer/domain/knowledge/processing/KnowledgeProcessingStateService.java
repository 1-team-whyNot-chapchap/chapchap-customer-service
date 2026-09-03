package com.chapchap.customer.domain.knowledge.processing;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.global.error.custom.knowledge.KnowledgeProcessingStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeProcessingStateService {
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final AuditLogWriter auditLogWriter;

    @Transactional
    public KnowledgeProcessingContext startAttempt(Long knowledgeVersionId, LocalDateTime now) {
        KnowledgeVersion knowledgeVersion = knowledgeVersionRepository.findById(knowledgeVersionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge Version을 찾을 수 없습니다."));
        KnowledgeDocument knowledgeDocument = knowledgeDocumentRepository.findById(knowledgeVersion.getKnowledgeDocumentId())
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge 문서를 찾을 수 없습니다."));
        knowledgeVersion.startProcessing(now);

        return new KnowledgeProcessingContext(
                knowledgeVersion.getId(),
                knowledgeVersion.getProcessingAttemptCount(),
                knowledgeVersion.getObjectKey(),
                knowledgeVersion.getContentType(),
                knowledgeVersion.getFileSize(),
                knowledgeDocument.getDocumentKey(),
                knowledgeDocument.getSourceService(),
                knowledgeDocument.getCategory(),
                knowledgeVersion.getVersion(),
                knowledgeVersion.getEffectiveFrom(),
                knowledgeVersion.getChunkProfile()
        );
    }

    @Transactional
    public void markCompleted(Long knowledgeVersionId, LocalDateTime now) {
        KnowledgeVersion knowledgeVersion = knowledgeVersionRepository.findById(knowledgeVersionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge Version을 찾을 수 없습니다."));
        knowledgeVersion.completeProcessing(now);
    }

    @Transactional
    public void markFailed(Long knowledgeVersionId, String failureCode, boolean retryable, LocalDateTime now) {
        KnowledgeVersion knowledgeVersion = knowledgeVersionRepository.findById(knowledgeVersionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge Version을 찾을 수 없습니다."));
        knowledgeVersion.failProcessing(failureCode, retryable, now);
        auditLogWriter.recordKnowledgeProcessingFailed(knowledgeVersion, now);
    }
}
