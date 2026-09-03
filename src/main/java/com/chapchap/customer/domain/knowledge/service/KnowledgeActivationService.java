package com.chapchap.customer.domain.knowledge.service;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeActivationService {
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final AuditLogWriter auditLogWriter;

    @Transactional
    public boolean activateIfDue(Long knowledgeVersionId, LocalDateTime now) {
        KnowledgeVersion target = knowledgeVersionRepository.findById(knowledgeVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge Version을 찾을 수 없습니다."));

        if (!target.canActivateAt(now)) {
            return false;
        }

        KnowledgeDocument document = knowledgeDocumentRepository.findById(target.getKnowledgeDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Knowledge 문서를 찾을 수 없습니다."));

        KnowledgeVersion lockedTarget = knowledgeVersionRepository.findById(target.getId())
                .orElseThrow(() -> new IllegalArgumentException("Knowledge Version을 찾을 수 없습니다."));
        if (!lockedTarget.canActivateAt(now)) {
            return false;
        }

        KnowledgeVersion preferredVersion = knowledgeVersionRepository
                .findFirstByKnowledgeDocumentIdAndProcessingStatusAndActiveFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                        document.getId(),
                        KnowledgeProcessingStatus.READY,
                        now
                )
                .orElseThrow(() -> new IllegalStateException("시행 가능한 READY Knowledge Version을 찾을 수 없습니다."));
        if (!preferredVersion.getId().equals(lockedTarget.getId())) {
            return false;
        }

        knowledgeVersionRepository.findByKnowledgeDocumentIdAndActiveTrue(document.getId())
                .filter(activeVersion -> !activeVersion.getId().equals(lockedTarget.getId()))
                .ifPresent(activeVersion -> activeVersion.deactivate(now));

        lockedTarget.activate(now);
        auditLogWriter.recordKnowledgeVersionActivated(lockedTarget, now);
        return true;
    }

}
