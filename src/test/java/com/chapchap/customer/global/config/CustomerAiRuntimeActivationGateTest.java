package com.chapchap.customer.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class CustomerAiRuntimeActivationGateTest {

    @Test
    void allowsDisabledModeWhenEveryRuntimeFlagIsOff() {
        assertThatCode(() -> gate(new MockEnvironment()).verify()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "customer.ai.internal-auth.enabled",
            "customer.ai.callback-auth.enabled",
            "customer.ai.knowledge-processing.async-enabled",
            "customer.ai.consultation-response.async-enabled",
            "customer.ai.consultation-summary.async-enabled"
    })
    void rejectsEveryPartialRuntimeFlagInDisabledMode(String property) {
        MockEnvironment enabledRuntime = new MockEnvironment()
                .withProperty(property, "true");
        assertThatThrownBy(() -> gate(enabledRuntime).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("비활성 모드에서는 Customer-AI Runtime을 켤 수 없습니다.");
    }

    @Test
    void allowsIsolatedModeOnlyWithDedicatedProfileAndSelectedRuntime() {
        MockEnvironment missingProfile = new MockEnvironment()
                .withProperty("customer.ai.activation.mode", "ISOLATED")
                .withProperty("customer.ai.callback-auth.enabled", "true");
        assertThatThrownBy(() -> gate(missingProfile).verify())
                .isInstanceOf(IllegalStateException.class);

        MockEnvironment isolated = new MockEnvironment()
                .withProperty("customer.ai.activation.mode", "ISOLATED")
                .withProperty("customer.ai.callback-auth.enabled", "true");
        isolated.setActiveProfiles("ai-isolated");
        assertThatCode(() -> gate(isolated).verify()).doesNotThrowAnyException();
    }

    @Test
    void rejectsActiveModeWhileVersionedManifestIsBlocked() {
        MockEnvironment active = new MockEnvironment()
                .withProperty("customer.ai.activation.mode", "ACTIVE")
                .withProperty("customer.ai.internal-auth.enabled", "true")
                .withProperty("customer.ai.callback-auth.enabled", "true")
                .withProperty("customer.ai.knowledge-processing.async-enabled", "true")
                .withProperty("customer.ai.consultation-response.async-enabled", "true")
                .withProperty("customer.ai.consultation-summary.async-enabled", "true");

        assertThatThrownBy(() -> gate(active).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Customer-AI 운영 활성화 증거가 완료되지 않았습니다.");
    }

    @Test
    void rejectsUnknownModeWithoutEchoingConfiguredValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("customer.ai.activation.mode", "BYPASS");

        assertThatThrownBy(() -> gate(environment).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("BYPASS");
    }

    private CustomerAiRuntimeActivationGate gate(MockEnvironment environment) {
        return new CustomerAiRuntimeActivationGate(environment, new ObjectMapper());
    }
}
