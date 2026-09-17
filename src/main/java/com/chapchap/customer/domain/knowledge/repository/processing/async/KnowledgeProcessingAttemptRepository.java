package com.chapchap.customer.domain.knowledge.repository.processing.async;

import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeProcessingAttemptRepository extends JpaRepository<KnowledgeProcessingAttempt, Long> {
    Optional<KnowledgeProcessingAttempt> findByRequestId(UUID requestId);
    boolean existsByKnowledgeProcessingJobIdAndRequestId(Long knowledgeProcessingJobId, UUID requestId);

    // 요청 ID로 연결된 Version ID만 읽는다. 이 조회 자체에는 쓰기 락을 걸지 않는다.
    @Query("""
            select job.knowledgeVersionId
            from KnowledgeProcessingAttempt attempt, KnowledgeProcessingJob job
            where attempt.knowledgeProcessingJobId = job.id
              and attempt.requestId = :requestId
            """)
    Optional<Long> findKnowledgeVersionIdByRequestId(@Param("requestId") UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt from KnowledgeProcessingAttempt attempt
            where attempt.knowledgeProcessingJobId = :jobId
              and attempt.attempt = :attemptNumber
            """)
    Optional<KnowledgeProcessingAttempt> findCurrentForUpdate(
            @Param("jobId") Long jobId, @Param("attemptNumber") int attemptNumber);
}
