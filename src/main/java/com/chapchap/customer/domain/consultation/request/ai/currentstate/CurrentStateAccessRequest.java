package com.chapchap.customer.domain.consultation.request.ai.currentstate;

import com.chapchap.customer.domain.consultation.constant.ai.currentstate.CurrentStateCapability;
import com.chapchap.customer.global.exception.consultation.ai.currentstate.CurrentStateAccessException;

import com.chapchap.customer.global.security.context.GatewayUserPrincipal;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record CurrentStateAccessRequest(
        UUID requestId,
        long consultationId,
        GatewayUserPrincipal principal,
        List<CurrentStateCapability> capabilities
) {
    public CurrentStateAccessRequest {
        if (requestId == null) {
            throw new CurrentStateAccessException("Current-State request context is invalid.");
        }
        if (consultationId <= 0) {
            throw new CurrentStateAccessException("Current-State request context is invalid.");
        }
        if (principal == null) {
            throw new CurrentStateAccessException("Authenticated subject is required.");
        }
        if (capabilities == null) {
            throw new CurrentStateAccessException("One or two approved capabilities are required.");
        }
        if (capabilities.size() < 1 || capabilities.size() > 2
                || capabilities.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(capabilities).size() != capabilities.size()) {
            throw new CurrentStateAccessException("One or two approved capabilities are required.");
        }
        capabilities = List.copyOf(capabilities);
    }
}
