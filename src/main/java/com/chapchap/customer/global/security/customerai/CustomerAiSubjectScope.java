package com.chapchap.customer.global.security.customerai;

import java.util.Set;

public enum CustomerAiSubjectScope {
    POLICY_READ("customer-ai.policy.read"),
    PAYMENT_READ("subscription.payment.read"),
    REFUND_READ("subscription.refund.read"),
    SUBSCRIPTION_READ("subscription.status.read"),
    DELIVERY_READ("delivery.status.read");

    private static final Set<String> APPROVED_VALUES = Set.of(
            POLICY_READ.value,
            PAYMENT_READ.value,
            REFUND_READ.value,
            SUBSCRIPTION_READ.value,
            DELIVERY_READ.value
    );

    private final String value;

    CustomerAiSubjectScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Set<String> approvedValues() {
        return APPROVED_VALUES;
    }
}
