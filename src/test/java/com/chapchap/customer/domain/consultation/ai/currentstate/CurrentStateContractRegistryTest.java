package com.chapchap.customer.domain.consultation.ai.currentstate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentStateContractRegistryTest {
    private static JsonNode currentState;

    @BeforeAll
    static void loadFixture() throws Exception {
        try (InputStream input = CurrentStateContractRegistryTest.class.getResourceAsStream(
                "/contracts/customer-ai-candidate-v1.json")) {
            assertThat(input).isNotNull();
            currentState = new ObjectMapper().readTree(input).required("currentState");
        }
    }

    @Test
    void registersOnlyTheFourApprovedCapabilitiesAndScopes() {
        assertThat(CurrentStateCapability.values()).extracting(CurrentStateCapability::id)
                .containsExactlyInAnyOrder(
                        "CAP-PAYMENT-CURRENT",
                        "CAP-REFUND-RECENT",
                        "CAP-SUBSCRIPTION-CURRENT",
                        "CAP-DELIVERY-CURRENT"
                );
        assertThat(CurrentStateCapability.values()).extracting(CurrentStateCapability::requiredScope)
                .containsExactlyInAnyOrder(
                        "subscription.payment.read",
                        "subscription.refund.read",
                        "subscription.status.read",
                        "delivery.status.read"
                );
        assertThat(CurrentStateCapability.values()).extracting(CurrentStateCapability::domainOwner)
                .containsOnly("subscription-service", "delivery-service");
    }

    @Test
    void keepsRuntimeAvailabilitySeparateFromContractErrors() {
        assertThat(CurrentStateAvailability.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("AVAILABLE", "NOT_FOUND", "UNAVAILABLE", "TIMEOUT", "FORBIDDEN");
        assertThat(CurrentStateErrorCode.values()).extracting(Enum::name)
                .containsExactly("CONTRACT_ERROR");
        assertThat(CurrentStateAvailability.values()).extracting(Enum::name)
                .doesNotContain("CONTRACT_ERROR");
    }

    @Test
    void candidateFixtureUsesExactMinimalFieldsAndApprovedStatuses() {
        Set<String> rootFields = new HashSet<>();
        rootFields.addAll(currentState.propertyNames());
        assertThat(rootFields).containsExactlyInAnyOrder("payment", "refund", "subscription", "delivery");

        for (CurrentStateCapability capability : CurrentStateCapability.values()) {
            JsonNode payload = currentState.required(capability.fixtureField());
            Set<String> actualFields = new HashSet<>();
            actualFields.addAll(payload.propertyNames());

            assertThat(actualFields).containsAll(capability.requiredFields()).isSubsetOf(capability.allowedFields());
            assertThat(payload.required("availability").textValue()).isEqualTo(CurrentStateAvailability.AVAILABLE.name());
            assertThat(payload.required("status").textValue()).isIn(capability.allowedStatuses());
            assertThat(actualFields).doesNotContain("userId", "id", "databaseId", "billingKey", "failureReasonCode");
        }
    }

    @Test
    void modelsNullableDeliveryTimestampAsOptionalWithoutAllowingExtraFields() {
        assertThat(CurrentStateCapability.DELIVERY_CURRENT.requiredFields()).doesNotContain("statusChangedAt");
        assertThat(CurrentStateCapability.DELIVERY_CURRENT.allowedFields()).contains("statusChangedAt");
    }

    @Test
    void rejectsProviderOnlyOrInternalStatusesAtTheConsumerBoundary() {
        assertThat(CurrentStateCapability.PAYMENT_CURRENT.allowedStatuses()).doesNotContain("COMPLETED");
        assertThat(CurrentStateCapability.DELIVERY_CURRENT.allowedStatuses()).doesNotContain("DELIVERED", "CANCELED");
        assertThat(CurrentStateCapability.DELIVERY_CURRENT.allowedStatuses()).contains("COMPLETED");
    }
}
