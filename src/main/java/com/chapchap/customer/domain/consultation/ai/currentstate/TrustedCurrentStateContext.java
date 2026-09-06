package com.chapchap.customer.domain.consultation.ai.currentstate;

import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.customerai.CustomerAiSubjectAssertionRequest;

import java.util.List;
import java.util.UUID;

public final class TrustedCurrentStateContext {
    private final long userId;
    private final RolePolicy role;
    private final UUID requestId;
    private final long consultationId;
    private final List<CurrentStateCapability> capabilities;
    private final List<String> allowedAiScopes;

    TrustedCurrentStateContext(
            long userId,
            RolePolicy role,
            UUID requestId,
            long consultationId,
            List<CurrentStateCapability> capabilities
    ) {
        this.userId = userId;
        this.role = role;
        this.requestId = requestId;
        this.consultationId = consultationId;
        this.capabilities = List.copyOf(capabilities);
        this.allowedAiScopes = this.capabilities.stream()
                .map(CurrentStateCapability::requiredScope)
                .toList();
    }

    public long userId() {
        return userId;
    }

    public RolePolicy role() {
        return role;
    }

    public UUID requestId() {
        return requestId;
    }

    public long consultationId() {
        return consultationId;
    }

    public List<CurrentStateCapability> capabilities() {
        return capabilities;
    }

    public List<String> allowedAiScopes() {
        return allowedAiScopes;
    }

    public CustomerAiSubjectAssertionRequest toSubjectAssertionRequest() {
        return new CustomerAiSubjectAssertionRequest(
                userId,
                role,
                allowedAiScopes,
                requestId,
                consultationId
        );
    }
}
