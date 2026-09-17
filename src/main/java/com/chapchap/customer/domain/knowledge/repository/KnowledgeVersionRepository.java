package com.chapchap.customer.domain.knowledge.repository;

import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KnowledgeVersionRepository extends JpaRepository<KnowledgeVersion, Long> {
    @Query("""
            select v.id from KnowledgeVersion v
            where v.active = true and v.processingStatus = :processingStatus
              and v.effectiveFrom <= :now order by v.id
            """)
    List<Long> findApprovedVersionIds(
            @Param("processingStatus") KnowledgeProcessingStatus processingStatus,
            @Param("now") LocalDateTime now);

    boolean existsByKnowledgeDocumentIdAndVersion(Long knowledgeDocumentId, String version);
    Optional<KnowledgeVersion> findByKnowledgeDocumentIdAndActiveTrue(Long knowledgeDocumentId);

    // 잠그기 전에 엔티티 전체를 영속성 컨텍스트에 올리지 않고, 불변인 연결 ID만 읽는다.
    @Query("select v.knowledgeDocumentId from KnowledgeVersion v where v.id = :versionId")
    Optional<Long> findDocumentIdByVersionId(@Param("versionId") Long versionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from KnowledgeVersion v where v.id = :knowledgeVersionId")
    Optional<KnowledgeVersion> findByIdForUpdate(
            @Param("knowledgeVersionId") Long knowledgeVersionId);

    Optional<KnowledgeVersion> findFirstByKnowledgeDocumentIdAndProcessingStatusAndActiveFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            Long knowledgeDocumentId, KnowledgeProcessingStatus processingStatus, LocalDateTime now);

    @Query("""
            select v.id from KnowledgeVersion v
            where v.processingStatus = :processingStatus
              and v.active = false and v.effectiveFrom <= :now
            """)
    List<Long> findActivatableVersionIds(
            @Param("processingStatus") KnowledgeProcessingStatus processingStatus,
            @Param("now") LocalDateTime now);
}
