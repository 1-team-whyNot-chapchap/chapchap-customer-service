package com.chapchap.customer.domain.knowledge.processing.async;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface KnowledgeProcessingJobRepository extends JpaRepository<KnowledgeProcessingJob, Long> {
    Optional<KnowledgeProcessingJob> findByKnowledgeVersionId(Long knowledgeVersionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from KnowledgeProcessingJob job where job.id = :jobId")
    Optional<KnowledgeProcessingJob> findByIdForUpdate(@Param("jobId") Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from KnowledgeProcessingJob job where job.processingId = :processingId")
    Optional<KnowledgeProcessingJob> findByProcessingIdForUpdate(@Param("processingId") Long processingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from KnowledgeProcessingJob job where job.knowledgeVersionId = :knowledgeVersionId")
    Optional<KnowledgeProcessingJob> findByKnowledgeVersionIdForUpdate(
            @Param("knowledgeVersionId") Long knowledgeVersionId
    );
}
