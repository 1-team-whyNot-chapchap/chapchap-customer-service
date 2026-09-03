package com.chapchap.customer.domain.knowledge.service;

import com.chapchap.customer.domain.knowledge.file.KnowledgeFileValidator;
import com.chapchap.customer.domain.knowledge.file.ValidatedKnowledgeFile;
import com.chapchap.customer.domain.knowledge.request.KnowledgeVersionRegisterRequest;
import com.chapchap.customer.domain.knowledge.response.KnowledgeVersionResponse;
import com.chapchap.customer.domain.knowledge.storage.KnowledgeObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeRegistrationService {
    private final KnowledgeFileValidator knowledgeFileValidator;
    private final KnowledgeObjectStorage knowledgeObjectStorage;
    private final KnowledgeRegistrationPersistenceService persistenceService;

    public KnowledgeVersionResponse register(Long actorUserId, KnowledgeVersionRegisterRequest request) {
        ValidatedKnowledgeFile file = knowledgeFileValidator.validate(request.getFile());
        String objectKey = "knowledge/" + UUID.randomUUID();
        knowledgeObjectStorage.store(
                objectKey,
                new ByteArrayInputStream(file.content()),
                file.size(),
                file.contentType()
        );

        try {
            return persistenceService.register(actorUserId, request, file, objectKey);
        } catch (RuntimeException exception) {
            try {
                knowledgeObjectStorage.delete(objectKey);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }
}
