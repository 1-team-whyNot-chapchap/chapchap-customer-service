package com.chapchap.customer.domain.consultation.service.ai.currentstate;

import com.chapchap.customer.domain.consultation.constant.ai.currentstate.CurrentStateCapability;
import com.chapchap.customer.domain.consultation.service.ai.currentstate.TrustedCurrentStateContext;
import com.chapchap.customer.global.exception.consultation.ai.currentstate.CurrentStateAccessException;
import com.chapchap.customer.domain.consultation.request.ai.currentstate.CurrentStateAccessRequest;

import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentStateTrustedContextBoundaryTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final CurrentStateTrustedContextBoundary boundary = new CurrentStateTrustedContextBoundary();

    @Test
    void createsSubjectOnlyFromAuthenticatedPrincipalAndSelectedCapability() {
        CurrentStateAccessRequest request = new CurrentStateAccessRequest(
                REQUEST_ID,
                501,
                new GatewayUserPrincipal("42", RolePolicy.CUSTOMER),
                List.of(CurrentStateCapability.PAYMENT_CURRENT)
        );

        TrustedCurrentStateContext context = boundary.create(request);
        CustomerAiSubjectAssertionRequest subject = context.toSubjectAssertionRequest();

        assertThat(context.userId()).isEqualTo(42);
        assertThat(context.role()).isEqualTo(RolePolicy.CUSTOMER);
        assertThat(context.requestId()).isEqualTo(REQUEST_ID);
        assertThat(context.consultationId()).isEqualTo(501);
        assertThat(context.capabilities()).containsExactly(CurrentStateCapability.PAYMENT_CURRENT);
        assertThat(context.allowedAiScopes()).containsExactly("subscription.payment.read");
        assertThat(subject.userId()).isEqualTo(42);
        assertThat(subject.allowedAiScopes()).containsExactly("subscription.payment.read");
    }

    @Test
    void preservesTwoCapabilityOrderAndGrantsNoAdditionalScope() {
        CurrentStateAccessRequest request = new CurrentStateAccessRequest(
                REQUEST_ID,
                501,
                new GatewayUserPrincipal("73", RolePolicy.RIDER),
                List.of(CurrentStateCapability.DELIVERY_CURRENT, CurrentStateCapability.REFUND_RECENT)
        );

        TrustedCurrentStateContext context = boundary.create(request);

        assertThat(context.capabilities()).containsExactly(
                CurrentStateCapability.DELIVERY_CURRENT,
                CurrentStateCapability.REFUND_RECENT
        );
        assertThat(context.allowedAiScopes()).containsExactly(
                "delivery.status.read",
                "subscription.refund.read"
        );
        assertThat(context.allowedAiScopes()).doesNotContain("customer-ai.policy.read");
    }

    @ParameterizedTest
    @EnumSource(value = RolePolicy.class, names = {"ADMIN", "SUPER_ADMIN"})
    void rejectsAdministrativeSubjects(RolePolicy role) {
        CurrentStateAccessRequest request = request(new GatewayUserPrincipal("42", role));

        assertThatThrownBy(() -> boundary.create(request))
                .isInstanceOf(CurrentStateAccessException.class)
                .hasMessage("Authenticated subject is not allowed.");
    }

    @Test
    void rejectsMissingSubjectRole() {
        CurrentStateAccessRequest request = request(new GatewayUserPrincipal("42", null));

        assertThatThrownBy(() -> boundary.create(request))
                .isInstanceOf(CurrentStateAccessException.class)
                .hasMessage("Authenticated subject is not allowed.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "01", "+1", "-1", " 42", "42 ", "9223372036854775808"})
    void rejectsNonCanonicalOrOutOfRangePrincipalUserId(String userId) {
        CurrentStateAccessRequest request = request(new GatewayUserPrincipal(userId, RolePolicy.CUSTOMER));

        assertThatThrownBy(() -> boundary.create(request))
                .isInstanceOf(CurrentStateAccessException.class)
                .hasMessage("Authenticated subject is invalid.");
    }

    @Test
    void rejectsMissingDuplicateOrMoreThanTwoCapabilities() {
        GatewayUserPrincipal principal = new GatewayUserPrincipal("42", RolePolicy.CUSTOMER);

        assertThatThrownBy(() -> new CurrentStateAccessRequest(REQUEST_ID, 501, principal, List.of()))
                .isInstanceOf(CurrentStateAccessException.class);
        assertThatThrownBy(() -> new CurrentStateAccessRequest(
                REQUEST_ID,
                501,
                principal,
                List.of(CurrentStateCapability.PAYMENT_CURRENT, CurrentStateCapability.PAYMENT_CURRENT)
        )).isInstanceOf(CurrentStateAccessException.class);
        assertThatThrownBy(() -> new CurrentStateAccessRequest(
                REQUEST_ID,
                501,
                principal,
                List.of(CurrentStateCapability.PAYMENT_CURRENT, CurrentStateCapability.REFUND_RECENT,
                        CurrentStateCapability.SUBSCRIPTION_CURRENT)
        )).isInstanceOf(CurrentStateAccessException.class);
        assertThatThrownBy(() -> new CurrentStateAccessRequest(
                REQUEST_ID,
                501,
                principal,
                Arrays.asList(CurrentStateCapability.PAYMENT_CURRENT, null)
        )).isInstanceOf(CurrentStateAccessException.class);
    }

    @Test
    void rejectsUnknownCapabilityWithoutEchoingUntrustedValue() {
        assertThatThrownBy(() -> CurrentStateCapability.fromId("CAP-ADMIN-ALL"))
                .isInstanceOf(CurrentStateAccessException.class)
                .hasMessage("Current-State capability is not approved.")
                .hasMessageNotContaining("CAP-ADMIN-ALL");
    }

    @Test
    void protectsCapabilitiesAndScopesFromMutation() {
        TrustedCurrentStateContext context = boundary.create(request(
                new GatewayUserPrincipal("42", RolePolicy.CUSTOMER)
        ));

        assertThatThrownBy(() -> context.capabilities().add(CurrentStateCapability.DELIVERY_CURRENT))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.allowedAiScopes().add("delivery.status.read"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingOrInvalidRequestContext() {
        GatewayUserPrincipal principal = new GatewayUserPrincipal("42", RolePolicy.CUSTOMER);

        assertThatThrownBy(() -> boundary.create(null))
                .isInstanceOf(CurrentStateAccessException.class);
        assertThatThrownBy(() -> new CurrentStateAccessRequest(null, 501, principal,
                List.of(CurrentStateCapability.PAYMENT_CURRENT)))
                .isInstanceOf(CurrentStateAccessException.class);
        assertThatThrownBy(() -> new CurrentStateAccessRequest(REQUEST_ID, 0, principal,
                List.of(CurrentStateCapability.PAYMENT_CURRENT)))
                .isInstanceOf(CurrentStateAccessException.class);
        assertThatThrownBy(() -> new CurrentStateAccessRequest(REQUEST_ID, 501, null,
                List.of(CurrentStateCapability.PAYMENT_CURRENT)))
                .isInstanceOf(CurrentStateAccessException.class);
    }

    private CurrentStateAccessRequest request(GatewayUserPrincipal principal) {
        return new CurrentStateAccessRequest(
                REQUEST_ID,
                501,
                principal,
                List.of(CurrentStateCapability.PAYMENT_CURRENT)
        );
    }
}
