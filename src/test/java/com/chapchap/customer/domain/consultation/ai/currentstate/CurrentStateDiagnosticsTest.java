package com.chapchap.customer.domain.consultation.ai.currentstate;

import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticEvent;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticOutcome;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentStateDiagnosticsTest {
    @Test
    void emitsOneCorrelatedEventForEachGrantedCapability() {
        List<CustomerAiDiagnosticEvent> events = new ArrayList<>();
        CurrentStateTrustedContextBoundary boundary = new CurrentStateTrustedContextBoundary(
                new CustomerAiDiagnosticPublisher(events::add, () -> "trace-state")
        );
        UUID requestId = UUID.fromString("11111111-1111-4111-8111-111111111111");

        TrustedCurrentStateContext context = boundary.create(new CurrentStateAccessRequest(
                requestId,
                501,
                new GatewayUserPrincipal("42", RolePolicy.CUSTOMER),
                List.of(CurrentStateCapability.PAYMENT_CURRENT, CurrentStateCapability.DELIVERY_CURRENT)
        ));

        assertThat(context.capabilities()).hasSize(2);
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.requestId()).isEqualTo(requestId);
            assertThat(event.consultationId()).isEqualTo(501);
            assertThat(event.traceId()).isEqualTo("trace-state");
            assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.ACCESS_GRANTED);
        });
        assertThat(events).extracting(CustomerAiDiagnosticEvent::capabilityId)
                .containsExactly(CurrentStateCapability.PAYMENT_CURRENT, CurrentStateCapability.DELIVERY_CURRENT);
    }

    @Test
    void failingSinkDoesNotChangeTrustedContextCreation() {
        CurrentStateTrustedContextBoundary boundary = new CurrentStateTrustedContextBoundary(
                new CustomerAiDiagnosticPublisher(event -> {
                    throw new IllegalStateException("sink unavailable");
                }, () -> null)
        );

        TrustedCurrentStateContext context = boundary.create(new CurrentStateAccessRequest(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                501,
                new GatewayUserPrincipal("42", RolePolicy.CUSTOMER),
                List.of(CurrentStateCapability.PAYMENT_CURRENT)
        ));

        assertThat(context.userId()).isEqualTo(42);
    }
}
