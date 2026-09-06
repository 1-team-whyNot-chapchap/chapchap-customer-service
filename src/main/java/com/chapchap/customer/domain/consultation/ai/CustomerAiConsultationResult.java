package com.chapchap.customer.domain.consultation.ai;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CustomerAiConsultationResult(
        UUID requestId,
        Decision decision,
        String answer,
        Route route,
        boolean degraded,
        boolean handoffRequired,
        List<Evidence> evidence
) {
    public CustomerAiConsultationResult {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(route);
        evidence = List.copyOf(Objects.requireNonNull(evidence));
    }

    public boolean requiresAdminHandoff() {
        return handoffRequired;
    }

    public enum Decision {
        ANSWER,
        HANDOFF,
        DEGRADED
    }

    public enum Route {
        POLICY,
        USER_STATE,
        POLICY_AND_STATE,
        UNSUPPORTED
    }

    public record Evidence(
            long knowledgeVersionId,
            String chunkId,
            int retrievalRank,
            double retrievalScore
    ) {
    }
}
