package com.chapchap.customer.domain.knowledge.service;

import com.chapchap.customer.domain.knowledge.entity.KnowledgeProcessingStatus;
import com.chapchap.customer.domain.knowledge.repository.KnowledgeVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "customer.knowledge.activation-scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KnowledgeActivationScheduler {
    private final KnowledgeActivationService knowledgeActivationService;
    private final KnowledgeVersionRepository knowledgeVersionRepository;

    @Scheduled(fixedDelayString = "${customer.knowledge.activation-scheduler.fixed-delay:60000}")
    public void activateDueVersions() {
        LocalDateTime now = LocalDateTime.now();
        knowledgeVersionRepository.findActivatableVersionIds(KnowledgeProcessingStatus.READY, now)
                .forEach(versionId -> knowledgeActivationService.activateIfDue(versionId, now));
    }
}
