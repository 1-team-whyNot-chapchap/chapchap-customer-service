package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationCommand;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;
import com.chapchap.customer.global.exception.consultation.ai.CustomerAiConsultationClientException;
import com.chapchap.customer.global.security.constant.RolePolicy;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** The server selects scopes from a closed mapping, never from model-provided scope strings. */
final class ConsultationPlanParser {
    private static final Map<String, String> SCOPES = Map.of(
            "CAP-PAYMENT-CURRENT", "subscription.payment.read",
            "CAP-REFUND-RECENT", "subscription.refund.read",
            "CAP-SUBSCRIPTION-CURRENT", "subscription.status.read",
            "CAP-DELIVERY-CURRENT", "delivery.status.read");

    static CustomerAiConsultationCommand parse(String body, CustomerAiConsultationCommand original) {
        try {
            if (body == null || body.length() > 4096) throw new IllegalArgumentException();
            var node = new ObjectMapper().readTree(body);
            Set<String> fields = new HashSet<>();
            node.properties().forEach(entry -> fields.add(entry.getKey()));
            if (!fields.equals(Set.of("schemaVersion", "requestId", "planId", "route", "capabilities"))
                    || !node.path("schemaVersion").asText().equals("1.0")
                    || !original.requestId().toString().equals(node.path("requestId").asText())) {
                throw new IllegalArgumentException();
            }
            UUID planId = UUID.fromString(node.path("planId").asText());
            String route = node.path("route").asText();
            if (!Set.of("POLICY", "USER_STATE", "POLICY_AND_STATE", "UNSUPPORTED").contains(route)) {
                throw new IllegalArgumentException();
            }
            var capabilities = node.get("capabilities");
            if (!capabilities.isArray() || capabilities.size() > 2) throw new IllegalArgumentException();
            List<String> scopes = new ArrayList<>(List.of("customer-ai.policy.read"));
            Set<String> seen = new HashSet<>();
            for (var capability : capabilities) {
                String code = capability.asText();
                if (!capability.isTextual() || !SCOPES.containsKey(code) || !seen.add(code)
                        || (code.equals("CAP-DELIVERY-CURRENT") && original.subject().role() != RolePolicy.CUSTOMER)) {
                    throw new IllegalArgumentException();
                }
                scopes.add(SCOPES.get(code));
            }
            boolean state = route.equals("USER_STATE") || route.equals("POLICY_AND_STATE");
            if (state != !seen.isEmpty()) throw new IllegalArgumentException();
            var subject = new CustomerAiSubjectAssertionRequest(original.subject().userId(),
                    original.subject().role(), List.copyOf(scopes), original.requestId(), original.consultationId());
            return new CustomerAiConsultationCommand(original.requestId(), original.consultationId(),
                    original.triggerMessageId(), subject, original.message(), original.conversationContext(),
                    original.knowledgeVersionIds(), planId);
        } catch (Exception error) {
            throw new CustomerAiConsultationClientException(
                    CustomerAiConsultationClientException.Reason.UNSAFE_RESPONSE);
        }
    }
}
