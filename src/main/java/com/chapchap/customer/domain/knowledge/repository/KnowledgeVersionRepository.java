package com.chapchap.customer.domain.knowledge.repository;

import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KnowledgeVersionRepository extends JpaRepository<KnowledgeVersion, Long> {
    boolean existsByKnowledgeDocumentIdAndVersion(Long knowledgeDocumentId, String version);

    Optional<KnowledgeVersion> findByKnowledgeDocumentIdAndActiveTrue(Long knowledgeDocumentId);

    Optional<KnowledgeVersion> findFirstByKnowledgeDocumentIdAndProcessingStatusAndActiveFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            Long knowledgeDocumentId,
            KnowledgeProcessingStatus processingStatus,
            LocalDateTime now
    );

    @Query("""
            select knowledgeVersion.id
            from KnowledgeVersion knowledgeVersion
            where knowledgeVersion.processingStatus = :processingStatus
              and knowledgeVersion.active = false
              and knowledgeVersion.effectiveFrom <= :now
            """)
    List<Long> findActivatableVersionIds(
            @Param("processingStatus") KnowledgeProcessingStatus processingStatus,
            @Param("now") LocalDateTime now
    );
}
