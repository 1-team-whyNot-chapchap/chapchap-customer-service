package com.chapchap.customer.domain.consultation.ai;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import com.chapchap.customer.domain.consultation.entity.ConsultationMessageSource;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageSourceRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(
        showSql = false,
        properties = {
                "spring.autoconfigure.exclude=",
                "spring.datasource.url=jdbc:h2:mem:consultation-ai;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class ConsultationAiPersistenceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 17, 0);

    @Autowired
    private ConsultationRepository consultationRepository;
    @Autowired
    private ConsultationMessageRepository messageRepository;
    @Autowired
    private ConsultationMessageSourceRepository sourceRepository;
    @Autowired
    private KnowledgeVersionRepository knowledgeVersionRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void enforcesOneAiResponsePerTriggerMessage() {
        Consultation consultation = consultationRepository.saveAndFlush(Consultation.create(42L, NOW));
        ConsultationMessage trigger = messageRepository.saveAndFlush(
                ConsultationMessage.firstUserMessage(consultation, 42L, "문의", NOW));
        messageRepository.saveAndFlush(ConsultationMessage.aiResponse(
                consultation, "첫 답변", 2, trigger.getId(), NOW));

        assertThatThrownBy(() -> messageRepository.saveAndFlush(ConsultationMessage.aiResponse(
                consultation, "중복 답변", 3, trigger.getId(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsEvidenceReferenceAndEnforcesChunkUniqueness() {
        Consultation consultation = consultationRepository.saveAndFlush(Consultation.create(42L, NOW));
        ConsultationMessage trigger = messageRepository.saveAndFlush(
                ConsultationMessage.firstUserMessage(consultation, 42L, "문의", NOW));
        ConsultationMessage answer = messageRepository.saveAndFlush(ConsultationMessage.aiResponse(
                consultation, "답변", 2, trigger.getId(), NOW));
        KnowledgeVersion version = KnowledgeVersion.uploaded(
                10L, "v1", "knowledge/refund.pdf", "refund.pdf",
                "application/pdf", 1024L, NOW, 11L, NOW);
        version.startProcessing(NOW.plusSeconds(1));
        version.completeProcessing(NOW.plusSeconds(2));
        version = knowledgeVersionRepository.saveAndFlush(version);
        Long knowledgeVersionId = version.getId();
        sourceRepository.saveAndFlush(ConsultationMessageSource.create(
                answer, version, "chunk-1", 1, 0.91234, NOW));
        entityManager.clear();

        ConsultationMessage stored = messageRepository.findByTriggerMessageId(trigger.getId()).orElseThrow();
        assertThat(stored.getContent()).isEqualTo("답변");

        assertThatThrownBy(() -> sourceRepository.saveAndFlush(ConsultationMessageSource.create(
                stored,
                knowledgeVersionRepository.findById(knowledgeVersionId).orElseThrow(),
                "chunk-1",
                2,
                0.8,
                NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
