package com.chapchap.customer.global.security.customerai;

import com.chapchap.customer.global.security.constant.RolePolicy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CustomerAiSubjectAssertionRequest(
        long userId,
        RolePolicy role,
        List<String> allowedAiScopes,
        UUID requestId,
        long consultationId
) {
    private static final long MAX_INT64 = Long.MAX_VALUE;

    public CustomerAiSubjectAssertionRequest {
        if (userId <= 0 || userId > MAX_INT64) {
            throw new IllegalArgumentException("userId must be a positive int64.");
        }
        Objects.requireNonNull(role, "role must not be null.");
        Objects.requireNonNull(allowedAiScopes, "allowedAiScopes must not be null.");
        Objects.requireNonNull(requestId, "requestId must not be null.");
        if (consultationId <= 0 || consultationId > MAX_INT64) {
            throw new IllegalArgumentException("consultationId must be a positive int64.");
        }

        allowedAiScopes = List.copyOf(allowedAiScopes);
        if (allowedAiScopes.isEmpty()
                || allowedAiScopes.stream().anyMatch(scope -> scope == null || scope.isBlank())
                || new HashSet<>(allowedAiScopes).size() != allowedAiScopes.size()) {
            throw new IllegalArgumentException("allowedAiScopes must contain unique non-blank values.");
        }
        if (!CustomerAiSubjectScope.approvedValues().containsAll(allowedAiScopes)) {
            throw new IllegalArgumentException("allowedAiScopes contains an unapproved value.");
        }
    }
}
