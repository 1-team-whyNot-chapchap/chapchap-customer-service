package com.chapchap.customer.domain.knowledge.service.processing;

import com.chapchap.customer.domain.knowledge.dto.processing.KnowledgeProcessingContext;
import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.global.exception.knowledge.KnowledgeProcessingStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class KnowledgeProcessingStateService {
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final AuditLogWriter auditLogWriter;

    public KnowledgeProcessingContext startAttempt(Long knowledgeVersionId, LocalDateTime now) {
        KnowledgeVersion version = requireVersion(knowledgeVersionId);
        KnowledgeDocument document = knowledgeDocumentRepository.findById(version.getKnowledgeDocumentId())
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge 문서를 찾을 수 없습니다."));
        version.startProcessing(now);
        return new KnowledgeProcessingContext(version.getId(), version.getProcessingAttemptCount(),
                version.getObjectKey(), version.getContentType(), version.getFileSize(), document.getDocumentKey(),
                document.getSourceService(), document.getCategory(), version.getVersion(),
                version.getEffectiveFrom(), version.getChunkProfile());
    }

    public void markCompleted(Long knowledgeVersionId, LocalDateTime now) {
        requireVersion(knowledgeVersionId).completeProcessing(now);
    }

    public void markFailed(Long knowledgeVersionId, String failureCode, boolean retryable, LocalDateTime now) {
        KnowledgeVersion version = requireVersion(knowledgeVersionId);
        version.failProcessing(failureCode, retryable, now);
        auditLogWriter.recordKnowledgeProcessingFailed(version, now);
    }

    private KnowledgeVersion requireVersion(Long knowledgeVersionId) {
        return knowledgeVersionRepository.findByIdForUpdate(knowledgeVersionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge Version을 찾을 수 없습니다."));
    }
}
