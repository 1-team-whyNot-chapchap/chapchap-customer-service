package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.dto.processing.KnowledgeProcessingContext;
import com.chapchap.customer.domain.knowledge.dto.processing.async.CustomerAiKnowledgeJobCommand;
import com.chapchap.customer.domain.knowledge.dto.processing.async.PreparedKnowledgeProcessingAttempt;
import com.chapchap.customer.domain.knowledge.response.processing.async.CustomerAiKnowledgeJobAccepted;
import com.chapchap.customer.domain.knowledge.service.storage.KnowledgeObjectStorage;
import com.chapchap.customer.global.exception.knowledge.processing.async.CustomerAiKnowledgeJobClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "customer.ai.knowledge-processing", name = "async-enabled", havingValue = "true")
public class KnowledgeAsyncProcessingOrchestrator {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock KST_CLOCK = Clock.system(KST);
    @Value("${customer.ai.transport.source-http-allowed-origins:}")
    private String sourceHttpAllowedOrigins = "";

    private final KnowledgeAsyncProcessingStateService stateService;
    private final CustomerAiKnowledgeJobClient customerAiKnowledgeJobClient;
    private final KnowledgeObjectStorage knowledgeObjectStorage;
    private final TaskScheduler knowledgeProcessingTaskScheduler;

    public void queueInitial(Long knowledgeVersionId) {
        knowledgeProcessingTaskScheduler.schedule(() -> submit(knowledgeVersionId, 0), Instant.now(KST_CLOCK));
    }

    public void queueRetry(Long knowledgeVersionId, int completedAttempt) {
        if (completedAttempt < 1 || completedAttempt >= 3) return;
        long delaySeconds = completedAttempt == 1 ? 5L : 30L;
        knowledgeProcessingTaskScheduler.schedule(() -> submit(knowledgeVersionId, completedAttempt),
                Instant.now(KST_CLOCK).plusSeconds(delaySeconds));
    }

    void submit(Long knowledgeVersionId) {
        submit(knowledgeVersionId, 0);
    }

    private void submit(Long knowledgeVersionId, int expectedCompletedAttempt) {
        UUID requestId = UUID.randomUUID();
        PreparedKnowledgeProcessingAttempt prepared = stateService.prepareAttempt(
                knowledgeVersionId, requestId, LocalDateTime.now(KST_CLOCK), expectedCompletedAttempt).orElse(null);
        if (prepared == null) return;
        CustomerAiKnowledgeJobCommand command;
        try {
            command = toCommand(prepared);
        } catch (IllegalArgumentException exception) {
            fail(requestId, CustomerAiKnowledgeJobClientException.Reason.CONTRACT_ERROR, false);
            return;
        } catch (RuntimeException exception) {
            fail(requestId, CustomerAiKnowledgeJobClientException.Reason.DEPENDENCY_UNAVAILABLE, true);
            return;
        }
        stateService.markSubmitted(requestId, LocalDateTime.now(KST_CLOCK));
        CustomerAiKnowledgeJobAccepted accepted;
        try {
            accepted = customerAiKnowledgeJobClient.submit(command);
        } catch (CustomerAiKnowledgeJobClientException exception) {
            fail(requestId, exception.reason(), exception.retryable());
            return;
        } catch (RuntimeException exception) {
            fail(requestId, CustomerAiKnowledgeJobClientException.Reason.DEPENDENCY_UNAVAILABLE, true);
            return;
        }
        stateService.markAccepted(requestId, accepted, LocalDateTime.now(KST_CLOCK));
    }

    private void fail(UUID requestId, CustomerAiKnowledgeJobClientException.Reason reason, boolean retryable) {
        stateService.markSubmissionFailed(requestId, reason, retryable, LocalDateTime.now(KST_CLOCK));
    }

    private CustomerAiKnowledgeJobCommand toCommand(PreparedKnowledgeProcessingAttempt prepared) {
        KnowledgeProcessingContext context = prepared.context();
        return new CustomerAiKnowledgeJobCommand(prepared.requestId(), context.knowledgeVersionId(), context.attempt(),
                new CustomerAiKnowledgeJobCommand.Source(
                        URI.create(knowledgeObjectStorage.createPresignedGetUrl(context.objectKey())),
                        context.contentType(), context.fileSize(),
                        com.chapchap.customer.global.config.customerai.CustomerAiTransportPolicy.httpOrigins(sourceHttpAllowedOrigins)),
                new CustomerAiKnowledgeJobCommand.Metadata(context.documentKey(), context.sourceService(),
                        context.category(), context.version(), context.effectiveFrom().atZone(KST).toOffsetDateTime()),
                context.chunkProfile());
    }
}
