package com.chapchap.customer.domain.knowledge.repository.processing.async;

import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface KnowledgeProcessingJobRepository extends JpaRepository<KnowledgeProcessingJob, Long> {
    Optional<KnowledgeProcessingJob> findByKnowledgeVersionId(Long knowledgeVersionId);

    // 콜백의 충돌 판정용 일반 조회. 존재하지 않는 processingId를 잠그지 않는다.
    boolean existsByProcessingId(Long processingId);

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
