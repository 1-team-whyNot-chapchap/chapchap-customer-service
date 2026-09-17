package com.chapchap.customer.domain.knowledge.service;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
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
public class KnowledgeActivationService {
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final AuditLogWriter auditLogWriter;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean activateIfDue(Long knowledgeVersionId, LocalDateTime now) {
        Long documentId = knowledgeVersionRepository.findDocumentIdByVersionId(knowledgeVersionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge Version을 찾을 수 없습니다."));

        // 활성화만 문서 단위로 직렬화한다. 처리/콜백에서는 Document 쓰기 락을 잡지 않는다.
        knowledgeDocumentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge 문서를 찾을 수 없습니다."));
        KnowledgeVersion target = knowledgeVersionRepository.findByIdForUpdate(knowledgeVersionId)
                .orElseThrow(() -> new KnowledgeProcessingStateException("Knowledge Version을 찾을 수 없습니다."));
        if (!documentId.equals(target.getKnowledgeDocumentId())) {
            throw new KnowledgeProcessingStateException("Knowledge 문서 연결이 변경되었습니다.");
        }
        if (!target.canActivateAt(now)) {
            return false;
        }

        KnowledgeVersion preferred = knowledgeVersionRepository
                .findFirstByKnowledgeDocumentIdAndProcessingStatusAndActiveFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                        documentId, KnowledgeProcessingStatus.READY, now)
                .orElse(null);
        if (preferred == null || !preferred.getId().equals(target.getId())) {
            return false;
        }

        KnowledgeVersion active = knowledgeVersionRepository
                .findByKnowledgeDocumentIdAndActiveTrue(documentId).orElse(null);
        if (active != null) {
            int dateOrder = active.getEffectiveFrom().compareTo(target.getEffectiveFrom());
            // 이미 더 최신 버전이 활성화됐다면 비활성인 옛 버전을 다시 활성화하지 않는다.
            if (dateOrder > 0 || (dateOrder == 0 && active.getId() >= target.getId())) {
                return false;
            }
            active.deactivate(now);
        }
        target.activate(now);
        auditLogWriter.recordKnowledgeVersionActivated(target, now);
        return true;
    }
}
