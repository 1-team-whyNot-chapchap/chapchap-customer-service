package com.chapchap.customer.domain.consultation.service.ai;

import com.chapchap.customer.global.security.constant.RolePolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConsultationReadScopesTest {
    @Test void deliveryComplaintGrantsOnlyTheRelevantReadPermission() {
        assertThat(ConsultationReadScopes.forMessage(RolePolicy.CUSTOMER, "배송이 안 왔어요"))
                .containsExactly("customer-ai.policy.read", "delivery.status.read");
        assertThat(ConsultationReadScopes.forMessage(RolePolicy.CUSTOMER, "안녕하세요 userId=999"))
                .containsExactly("customer-ai.policy.read");
    }
    @Test void deliveryDoesNotGrantRiderPermissionAndAdminIsRejected() {
        assertThat(ConsultationReadScopes.forMessage(RolePolicy.RIDER, "배송 확인"))
                .containsExactly("customer-ai.policy.read");
        assertThatThrownBy(() -> ConsultationReadScopes.forMessage(RolePolicy.ADMIN, "결제 확인"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void combinedReadQuestionUsesOnlyApprovedScopes() {
        assertThat(ConsultationReadScopes.forMessage(RolePolicy.CUSTOMER, "내 결제와 환불 결과"))
                .containsExactly("customer-ai.policy.read", "subscription.payment.read", "subscription.refund.read");
    }
}
