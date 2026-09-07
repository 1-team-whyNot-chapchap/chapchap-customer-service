package com.chapchap.customer.domain.consultation.ai.currentstate;

import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticEvent;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class CurrentStateTrustedContextBoundary {
    private static final Pattern POSITIVE_INT64 = Pattern.compile("[1-9][0-9]*");
    private static final Set<RolePolicy> ALLOWED_ROLES = Set.of(RolePolicy.CUSTOMER, RolePolicy.RIDER);

    private final CustomerAiDiagnosticPublisher diagnostics;

    public CurrentStateTrustedContextBoundary() {
        this(CustomerAiDiagnosticPublisher.noOp());
    }

    public CurrentStateTrustedContextBoundary(CustomerAiDiagnosticPublisher diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public TrustedCurrentStateContext create(CurrentStateAccessRequest request) {
        if (request == null) {
            throw new CurrentStateAccessException("Current-State request is required.");
        }

        GatewayUserPrincipal principal = request.principal();
        if (principal.role() == null || !ALLOWED_ROLES.contains(principal.role())) {
            publishDenied(request, CustomerAiDiagnosticFailureCode.FORBIDDEN);
            throw new CurrentStateAccessException("Authenticated subject is not allowed.");
        }

        long userId;
        try {
            userId = parseUserId(principal.userId());
        } catch (CurrentStateAccessException exception) {
            publishDenied(request, CustomerAiDiagnosticFailureCode.AUTHENTICATION_REJECTED);
            throw exception;
        }
        TrustedCurrentStateContext context = new TrustedCurrentStateContext(
                userId,
                principal.role(),
                request.requestId(),
                request.consultationId(),
                request.capabilities()
        );
        for (CurrentStateCapability capability : context.capabilities()) {
            diagnostics.publish(traceId -> CustomerAiDiagnosticEvent.currentStateAccessGranted(
                    context.requestId(), traceId, context.consultationId(), capability));
        }
        return context;
    }

    private void publishDenied(
            CurrentStateAccessRequest request,
            CustomerAiDiagnosticFailureCode failureCode
    ) {
        for (CurrentStateCapability capability : request.capabilities()) {
            diagnostics.publish(traceId -> CustomerAiDiagnosticEvent.currentStateAccessDenied(
                    request.requestId(), traceId, request.consultationId(), capability, failureCode));
        }
    }

    private long parseUserId(String value) {
        if (value == null || !POSITIVE_INT64.matcher(value).matches()) {
            throw new CurrentStateAccessException("Authenticated subject is invalid.");
        }
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) {
                throw new CurrentStateAccessException("Authenticated subject is invalid.");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new CurrentStateAccessException("Authenticated subject is invalid.");
        }
    }
}
