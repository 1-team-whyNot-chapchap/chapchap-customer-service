package com.chapchap.customer.domain.knowledge.processing.async;

import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.entity.processing.async.KnowledgeProcessingJob;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingAttemptRepository;
import com.chapchap.customer.domain.knowledge.repository.processing.async.KnowledgeProcessingJobRepository;

import jakarta.persistence.EntityManager;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.constant.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(
        showSql = false,
        properties = {
                "spring.autoconfigure.exclude=",
                "spring.datasource.url=jdbc:h2:mem:knowledge-callback;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class KnowledgeProcessingPersistenceTest {
    @Autowired
    private KnowledgeVersionRepository versionRepository;

    @Test
    void approvedVersionsRequireActiveReadyAndEffectiveDateIncludingBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 16, 0);
        KnowledgeVersion approved = version(1L, now);
        approved.startProcessing(now);
        approved.completeProcessing(now);
        approved.activate(now);
        KnowledgeVersion inactive = version(2L, now);
        inactive.startProcessing(now);
        inactive.completeProcessing(now);
        KnowledgeVersion future = version(3L, now.plusSeconds(1));
        future.startProcessing(now);
        future.completeProcessing(now);
        ReflectionTestUtils.setField(future, "active", true);
        KnowledgeVersion unprocessed = version(4L, now);
        ReflectionTestUtils.setField(unprocessed, "active", true);
        versionRepository.saveAllAndFlush(java.util.List.of(approved, inactive, future, unprocessed));
        entityManager.clear();

        assertThat(versionRepository.findApprovedVersionIds(KnowledgeProcessingStatus.READY, now))
                .containsExactly(approved.getId());
    }

    private KnowledgeVersion version(long documentId, LocalDateTime effectiveFrom) {
        return KnowledgeVersion.uploaded(documentId, "v1", "policy/" + documentId + ".pdf",
                "policy.pdf", "application/pdf", 100L, effectiveFrom, 42L, effectiveFrom);
    }

    @Autowired
    private KnowledgeProcessingJobRepository jobRepository;

    @Autowired
    private KnowledgeProcessingAttemptRepository attemptRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsUuidAsRequestIdentityAndLoadsLockedCurrentAttempt() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 16, 0);
        KnowledgeProcessingJob job = jobRepository.saveAndFlush(
                KnowledgeProcessingJob.create(101L, "HYBRID_POLICY_V1", now));
        UUID requestId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        KnowledgeProcessingAttempt attempt = attemptRepository.saveAndFlush(
                KnowledgeProcessingAttempt.create(job.getId(), 1, requestId, now));
        entityManager.clear();

        KnowledgeProcessingAttempt loaded = attemptRepository
                .findCurrentForUpdate(job.getId(), 1)
                .orElseThrow();

        assertThat(loaded.getId()).isEqualTo(attempt.getId());
        assertThat(loaded.getRequestId()).isEqualTo(requestId);
        assertThat(attemptRepository.findByRequestId(requestId)).isPresent();
    }

    @Test
    void enforcesOneLogicalJobPerKnowledgeVersion() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 16, 0);
        jobRepository.saveAndFlush(KnowledgeProcessingJob.create(
                101L, "HYBRID_POLICY_V1", now));

        assertThatThrownBy(() -> jobRepository.saveAndFlush(
                KnowledgeProcessingJob.create(101L, "HYBRID_POLICY_V1", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
