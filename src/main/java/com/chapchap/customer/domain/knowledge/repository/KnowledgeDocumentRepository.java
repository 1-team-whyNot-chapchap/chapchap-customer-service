package com.chapchap.customer.domain.knowledge.repository;

import com.chapchap.customer.domain.knowledge.entity.KnowledgeDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findBySourceServiceAndDocumentKey(
            String sourceService, String documentKey);

    // findById()는 JpaRepository의 일반 조회를 그대로 사용한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from KnowledgeDocument d where d.id = :id")
    Optional<KnowledgeDocument> findByIdForUpdate(@Param("id") Long id);
}
