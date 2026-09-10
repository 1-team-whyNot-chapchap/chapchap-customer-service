package com.chapchap.customer.domain.consultation.ai.currentstate;

import com.chapchap.customer.domain.consultation.constant.ai.currentstate.CurrentStateCapability;
import com.chapchap.customer.domain.consultation.service.ai.currentstate.TrustedCurrentStateContext;
import com.chapchap.customer.global.exception.consultation.ai.currentstate.CurrentStateAccessException;
import com.chapchap.customer.domain.consultation.request.ai.currentstate.CurrentStateAccessRequest;
import com.chapchap.customer.domain.consultation.service.ai.currentstate.CurrentStateTrustedContextBoundary;

import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticEventType;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticOutcome;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void emitsOneForbiddenEventForEachCapabilityWithoutUserIdentifier() {
        List<CustomerAiDiagnosticEvent> events = new ArrayList<>();
        CurrentStateTrustedContextBoundary boundary = new CurrentStateTrustedContextBoundary(
                new CustomerAiDiagnosticPublisher(events::add, () -> "trace-denied")
        );
        UUID requestId = UUID.fromString("11111111-1111-4111-8111-111111111111");

        assertThatThrownBy(() -> boundary.create(new CurrentStateAccessRequest(
                requestId,
                501,
                new GatewayUserPrincipal("42", RolePolicy.ADMIN),
                List.of(CurrentStateCapability.PAYMENT_CURRENT, CurrentStateCapability.DELIVERY_CURRENT)
        ))).isInstanceOf(CurrentStateAccessException.class);

        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.eventType()).isEqualTo(CustomerAiDiagnosticEventType.CURRENT_STATE_ACCESS_DENIED);
            assertThat(event.requestId()).isEqualTo(requestId);
            assertThat(event.consultationId()).isEqualTo(501);
            assertThat(event.traceId()).isEqualTo("trace-denied");
            assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.ACCESS_DENIED);
            assertThat(event.failureCode()).isEqualTo(CustomerAiDiagnosticFailureCode.FORBIDDEN);
            assertThat(event.metricDimensions()).doesNotContainKeys(
                    "requestId", "traceId", "consultationId", "userId"
            );
        });
        assertThat(events).extracting(CustomerAiDiagnosticEvent::capabilityId)
                .containsExactly(CurrentStateCapability.PAYMENT_CURRENT, CurrentStateCapability.DELIVERY_CURRENT);
    }

    @Test
    void emitsAuthenticationRejectedWithoutEchoingInvalidUserIdentifier() {
        List<CustomerAiDiagnosticEvent> events = new ArrayList<>();
        CurrentStateTrustedContextBoundary boundary = new CurrentStateTrustedContextBoundary(
                new CustomerAiDiagnosticPublisher(events::add, () -> null)
        );

        assertThatThrownBy(() -> boundary.create(new CurrentStateAccessRequest(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                501,
                new GatewayUserPrincipal("9223372036854775808", RolePolicy.CUSTOMER),
                List.of(CurrentStateCapability.SUBSCRIPTION_CURRENT)
        ))).isInstanceOf(CurrentStateAccessException.class)
                .hasMessageNotContaining("9223372036854775808");

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(CustomerAiDiagnosticOutcome.ACCESS_DENIED);
            assertThat(event.failureCode()).isEqualTo(CustomerAiDiagnosticFailureCode.AUTHENTICATION_REJECTED);
            assertThat(event.capabilityId()).isEqualTo(CurrentStateCapability.SUBSCRIPTION_CURRENT);
        });
    }

    @Test
    void failingSinkDoesNotChangeAccessRejection() {
        CurrentStateTrustedContextBoundary boundary = new CurrentStateTrustedContextBoundary(
                new CustomerAiDiagnosticPublisher(event -> {
                    throw new IllegalStateException("sink unavailable");
                }, () -> null)
        );

        assertThatThrownBy(() -> boundary.create(new CurrentStateAccessRequest(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                501,
                new GatewayUserPrincipal("42", RolePolicy.ADMIN),
                List.of(CurrentStateCapability.PAYMENT_CURRENT)
        ))).isInstanceOf(CurrentStateAccessException.class)
                .hasMessage("Authenticated subject is not allowed.");
    }
}
