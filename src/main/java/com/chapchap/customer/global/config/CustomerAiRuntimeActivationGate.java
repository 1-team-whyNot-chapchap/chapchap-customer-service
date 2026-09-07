package com.chapchap.customer.global.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class CustomerAiRuntimeActivationGate implements InitializingBean {
    private static final String MANIFEST = "/contracts/customer-ai-bilateral-matrix-v1.json";
    private static final String ISOLATED_PROFILE = "ai-isolated";
    private static final List<String> RUNTIME_FLAGS = List.of(
            "customer.ai.internal-auth.enabled",
            "customer.ai.callback-auth.enabled",
            "customer.ai.knowledge-processing.async-enabled",
            "customer.ai.consultation-response.async-enabled",
            "customer.ai.consultation-summary.async-enabled"
    );

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final CustomerAiActivationManifestVerifier manifestVerifier;

    public CustomerAiRuntimeActivationGate(Environment environment, ObjectMapper objectMapper) {
        this(environment, objectMapper, new CustomerAiActivationManifestVerifier());
    }

    CustomerAiRuntimeActivationGate(
            Environment environment,
            ObjectMapper objectMapper,
            CustomerAiActivationManifestVerifier manifestVerifier
    ) {
        this.environment = Objects.requireNonNull(environment);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.manifestVerifier = Objects.requireNonNull(manifestVerifier);
    }

    @Override
    public void afterPropertiesSet() {
        verify();
    }

    void verify() {
        CustomerAiActivationMode mode = activationMode();
        boolean anyRuntimeEnabled = RUNTIME_FLAGS.stream().anyMatch(this::isEnabled);

        switch (mode) {
            case DISABLED -> requireDisabled(anyRuntimeEnabled);
            case ISOLATED -> requireIsolated(anyRuntimeEnabled);
            case ACTIVE -> requireActive();
        }
    }

    private CustomerAiActivationMode activationMode() {
        String configured = environment.getProperty(
                "customer.ai.activation.mode",
                CustomerAiActivationMode.DISABLED.name()
        );
        try {
            return CustomerAiActivationMode.valueOf(configured);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Customer-AI 활성화 모드가 유효하지 않습니다.");
        }
    }

    private void requireDisabled(boolean anyRuntimeEnabled) {
        if (anyRuntimeEnabled) {
            throw new IllegalStateException("비활성 모드에서는 Customer-AI Runtime을 켤 수 없습니다.");
        }
    }

    private void requireIsolated(boolean anyRuntimeEnabled) {
        boolean isolatedProfile = Arrays.asList(environment.getActiveProfiles()).contains(ISOLATED_PROFILE);
        if (!isolatedProfile || !anyRuntimeEnabled) {
            throw new IllegalStateException("격리 모드는 전용 Profile과 검증 대상 Runtime이 필요합니다.");
        }
    }

    private void requireActive() {
        boolean allRuntimeEnabled = RUNTIME_FLAGS.stream().allMatch(this::isEnabled);
        boolean logsEnabled = environment.getProperty("customer.ai.observability.enabled", Boolean.class, true);
        if (!allRuntimeEnabled || !logsEnabled || !manifestVerifier.isReady(readManifest())) {
            throw new IllegalStateException("Customer-AI 운영 활성화 증거가 완료되지 않았습니다.");
        }
    }

    private boolean isEnabled(String property) {
        return environment.getProperty(property, Boolean.class, false);
    }

    private JsonNode readManifest() {
        try (InputStream input = CustomerAiRuntimeActivationGate.class.getResourceAsStream(MANIFEST)) {
            if (input == null) {
                throw new IllegalStateException("Customer-AI 활성화 manifest를 찾을 수 없습니다.");
            }
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Customer-AI 활성화 manifest를 읽을 수 없습니다.");
        }
    }
}
