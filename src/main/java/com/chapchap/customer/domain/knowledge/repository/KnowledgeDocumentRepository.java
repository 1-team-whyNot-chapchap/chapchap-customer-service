package com.chapchap.customer.domain.knowledge.repository;

import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findBySourceServiceAndDocumentKey(String sourceService, String documentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<KnowledgeDocument> findById(Long knowledgeDocumentId);
}
