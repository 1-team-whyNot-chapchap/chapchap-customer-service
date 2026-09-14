package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.domain.consultation.dto.ai.CustomerAiConsultationCommand;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;
import com.chapchap.customer.global.security.constant.RolePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConsultationPlanParserTest {
    private CustomerAiConsultationCommand command(RolePolicy role) {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        return new CustomerAiConsultationCommand(id, 501, 9002,
                new CustomerAiSubjectAssertionRequest(42L, role, List.of("customer-ai.policy.read"), id, 501),
                "지금 배달중인거 있어?", List.of());
    }
    private String body(String caps, String route) {
        return """
                {"schemaVersion":"1.0","requestId":"11111111-1111-4111-8111-111111111111",
                 "planId":"22222222-2222-4222-8222-222222222222","route":"%s","capabilities":%s}
                """.formatted(route, caps);
    }
    @Test void grantsOnlyMappedScopeAndPreservesSubjectAndTrigger() {
        var input = command(RolePolicy.CUSTOMER);
        var output = ConsultationPlanParser.parse(body("[\"CAP-DELIVERY-CURRENT\"]", "USER_STATE"), input);
        assertThat(output.subject().allowedAiScopes()).containsExactly("customer-ai.policy.read", "delivery.status.read");
        assertThat(output.subject().userId()).isEqualTo(input.subject().userId());
        assertThat(output.triggerMessageId()).isEqualTo(input.triggerMessageId());
        assertThat(output.message()).isEqualTo(input.message());
        assertThat(output.planId()).isNotNull();
    }
    @ParameterizedTest @ValueSource(strings = {
            "[\"ADMIN\"]", "[\"CAP-DELIVERY-CURRENT\",\"CAP-DELIVERY-CURRENT\"]", "[]",
            "[\"CAP-PAYMENT-CURRENT\",\"CAP-REFUND-RECENT\",\"CAP-SUBSCRIPTION-CURRENT\"]"})
    void rejectsUnknownDuplicateEmptyOrExcessiveStateCapabilities(String caps) {
        assertThatThrownBy(() -> ConsultationPlanParser.parse(body(caps, "USER_STATE"), command(RolePolicy.CUSTOMER)))
                .isInstanceOf(RuntimeException.class);
    }
    @Test void riderCannotReceiveCustomerDeliveryScope() {
        assertThatThrownBy(() -> ConsultationPlanParser.parse(body("[\"CAP-DELIVERY-CURRENT\"]", "USER_STATE"), command(RolePolicy.RIDER)))
                .isInstanceOf(RuntimeException.class);
    }
    @Test void rejectsMismatchedRequestAndAdditionalScopeField() {
        String valid = body("[]", "UNSUPPORTED");
        assertThatThrownBy(() -> ConsultationPlanParser.parse(valid.replace("11111111-1111-4111-8111-111111111111", UUID.randomUUID().toString()), command(RolePolicy.CUSTOMER)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> ConsultationPlanParser.parse(valid.replace("\"schemaVersion\"", "\"scope\":\"admin\",\"schemaVersion\""), command(RolePolicy.CUSTOMER)))
                .isInstanceOf(RuntimeException.class);
    }
}
