package com.chapchap.customer.domain.knowledge.service;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.event.KnowledgeVersionRegisteredEvent;
import com.chapchap.customer.domain.knowledge.file.ValidatedKnowledgeFile;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeDocumentRepository;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import com.chapchap.customer.domain.knowledge.request.KnowledgeVersionRegisterRequest;
import com.chapchap.customer.domain.knowledge.response.KnowledgeVersionResponse;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeRegistrationPersistenceService {
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVersionRepository knowledgeVersionRepository;
    private final AuditLogWriter auditLogWriter;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public KnowledgeVersionResponse register(
            Long actorUserId,
            KnowledgeVersionRegisterRequest request,
            ValidatedKnowledgeFile file,
            String objectKey
    ) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument knowledgeDocument = knowledgeDocumentRepository
                .findBySourceServiceAndDocumentKey(request.getSourceService(), request.getDocumentKey())
                .orElseGet(() -> knowledgeDocumentRepository.save(KnowledgeDocument.create(
                        request.getDocumentKey(),
                        request.getSourceService(),
                        request.getCategory(),
                        request.getTitle(),
                        now
                )));

        if (knowledgeVersionRepository.existsByKnowledgeDocumentIdAndVersion(knowledgeDocument.getId(), request.getVersion())) {
            throw new BusinessException(CustomResponseCode.DUPLICATED_RESOURCE_ERROR, "같은 Knowledge Version이 이미 등록되어 있습니다.");
        }

        KnowledgeVersion knowledgeVersion = knowledgeVersionRepository.save(KnowledgeVersion.uploaded(
                knowledgeDocument.getId(),
                request.getVersion(),
                objectKey,
                file.originalFilename(),
                file.contentType(),
                file.size(),
                request.getEffectiveFrom(),
                actorUserId,
                now
        ));
        auditLogWriter.recordKnowledgeVersionRegistered(actorUserId, knowledgeVersion, now);
        applicationEventPublisher.publishEvent(new KnowledgeVersionRegisteredEvent(knowledgeVersion.getId()));
        return KnowledgeVersionResponse.from(knowledgeVersion);
    }
}
