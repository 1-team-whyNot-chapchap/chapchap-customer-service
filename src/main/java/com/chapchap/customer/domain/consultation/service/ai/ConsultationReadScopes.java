package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.customerai.constant.security.CustomerAiSubjectScope;
import com.chapchap.customer.global.security.constant.RolePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selects existing read permissions; never derives the authenticated subject from chat text. */
final class ConsultationReadScopes {
    private ConsultationReadScopes() {}

    static List<String> forMessage(RolePolicy role, String message) {
        if (role != RolePolicy.CUSTOMER && role != RolePolicy.RIDER) {
            throw new IllegalArgumentException("Unsupported consultation role");
        }
        String text = message.toLowerCase(Locale.ROOT);
        List<String> scopes = new ArrayList<>();
        scopes.add(CustomerAiSubjectScope.POLICY_READ.value());
        if (text.contains("결제") || text.contains("payment")) scopes.add(CustomerAiSubjectScope.PAYMENT_READ.value());
        if (text.contains("환불") || text.contains("refund")) scopes.add(CustomerAiSubjectScope.REFUND_READ.value());
        if (text.contains("구독") || text.contains("subscription")) scopes.add(CustomerAiSubjectScope.SUBSCRIPTION_READ.value());
        if (role == RolePolicy.CUSTOMER && (text.contains("배송") || text.contains("delivery"))) {
            scopes.add(CustomerAiSubjectScope.DELIVERY_READ.value());
        }
        return List.copyOf(scopes);
    }
}
