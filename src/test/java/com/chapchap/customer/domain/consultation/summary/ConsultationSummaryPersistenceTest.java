package com.chapchap.customer.domain.consultation.summary;

import jakarta.persistence.EntityManager;
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
                "spring.datasource.url=jdbc:h2:mem:consultation-summary;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class ConsultationSummaryPersistenceTest {
    @Autowired
    private ConsultationSummaryJobRepository jobRepository;
    @Autowired
    private ConsultationSummaryRepository summaryRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsUuidAndLoadsJobWithLock() {
        UUID requestId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        ConsultationSummaryJob saved = jobRepository.saveAndFlush(
                ConsultationSummaryJob.create(501L, requestId, LocalDateTime.now()));
        entityManager.clear();

        ConsultationSummaryJob loaded = jobRepository.findByIdForUpdate(saved.getId()).orElseThrow();

        assertThat(loaded.getRequestId()).isEqualTo(requestId);
        assertThat(loaded.getConsultationId()).isEqualTo(501L);
    }

    @Test
    void enforcesOneSummaryJobPerConsultation() {
        LocalDateTime now = LocalDateTime.now();
        jobRepository.saveAndFlush(ConsultationSummaryJob.create(501L, UUID.randomUUID(), now));

        assertThatThrownBy(() -> jobRepository.saveAndFlush(
                ConsultationSummaryJob.create(501L, UUID.randomUUID(), now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesOneSummaryResultPerConsultation() {
        LocalDateTime now = LocalDateTime.now();
        summaryRepository.saveAndFlush(ConsultationSummary.fromAi(501L, "첫 요약", now));

        assertThatThrownBy(() -> summaryRepository.saveAndFlush(
                ConsultationSummary.fromAi(501L, "중복 요약", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
