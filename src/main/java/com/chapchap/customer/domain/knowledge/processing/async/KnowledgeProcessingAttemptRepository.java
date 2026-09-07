package com.chapchap.customer.domain.knowledge.processing.async;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt
            from KnowledgeProcessingAttempt attempt
            where attempt.knowledgeProcessingJobId = :jobId
              and attempt.attempt = :attemptNumber
            """)
    Optional<KnowledgeProcessingAttempt> findCurrentForUpdate(
            @Param("jobId") Long jobId,
            @Param("attemptNumber") int attemptNumber
    );
}
