package com.chapchap.customer.domain.consultation.constant.ai.currentstate;

import com.chapchap.customer.global.exception.consultation.ai.currentstate.CurrentStateAccessException;

import java.util.Arrays;
import java.util.Set;

public enum CurrentStateCapability {
    PAYMENT_CURRENT(
            "CAP-PAYMENT-CURRENT",
            "payment",
            "subscription.payment.read",
            "subscription-service",
            Set.of("PROCESSING", "SUCCESS", "RETRY_WAITING", "RETRY_STOPPED", "FAILED"),
            Set.of("availability", "status", "paymentType", "amount", "occurredAt"),
            Set.of()
    ),
    REFUND_RECENT(
            "CAP-REFUND-RECENT",
            "refund",
            "subscription.refund.read",
            "subscription-service",
            Set.of("PENDING", "COMPLETED", "FAILED", "REVIEW_REQUIRED"),
            Set.of("availability", "status", "refundType", "requestedAmount", "refundedAmount",
                    "unprocessedAmount", "requestedAt", "completedAt"),
            Set.of()
    ),
    SUBSCRIPTION_CURRENT(
            "CAP-SUBSCRIPTION-CURRENT",
            "subscription",
            "subscription.status.read",
            "subscription-service",
            Set.of("AWAITING_CONFIRMATION", "SCHEDULED", "IN_PROGRESS", "CANCELLATION_SCHEDULED",
                    "PAYMENT_FAILED", "CANCELED_BEFORE_START", "ENDED"),
            Set.of("availability", "status"),
            Set.of()
    ),
    DELIVERY_CURRENT(
            "CAP-DELIVERY-CURRENT",
            "delivery",
            "delivery.status.read",
            "delivery-service",
            Set.of("READY", "DELIVERING", "COMPLETED", "FAILED"),
            Set.of("availability", "status", "delayStatus"),
            Set.of("statusChangedAt")
    );

    private final String id;
    private final String fixtureField;
    private final String requiredScope;
    private final String domainOwner;
    private final Set<String> allowedStatuses;
    private final Set<String> requiredFields;
    private final Set<String> optionalFields;

    CurrentStateCapability(
            String id,
            String fixtureField,
            String requiredScope,
            String domainOwner,
            Set<String> allowedStatuses,
            Set<String> requiredFields,
            Set<String> optionalFields
    ) {
        this.id = id;
        this.fixtureField = fixtureField;
        this.requiredScope = requiredScope;
        this.domainOwner = domainOwner;
        this.allowedStatuses = Set.copyOf(allowedStatuses);
        this.requiredFields = Set.copyOf(requiredFields);
        this.optionalFields = Set.copyOf(optionalFields);
    }

    public String id() {
        return id;
    }

    public String fixtureField() {
        return fixtureField;
    }

    public String requiredScope() {
        return requiredScope;
    }

    public String domainOwner() {
        return domainOwner;
    }

    public Set<String> allowedStatuses() {
        return allowedStatuses;
    }

    public Set<String> requiredFields() {
        return requiredFields;
    }

    public Set<String> allowedFields() {
        java.util.HashSet<String> fields = new java.util.HashSet<>(requiredFields);
        fields.addAll(optionalFields);
        return Set.copyOf(fields);
    }

    public static CurrentStateCapability fromId(String id) {
        return Arrays.stream(values())
                .filter(capability -> capability.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new CurrentStateAccessException("Current-State capability is not approved."));
    }
}
