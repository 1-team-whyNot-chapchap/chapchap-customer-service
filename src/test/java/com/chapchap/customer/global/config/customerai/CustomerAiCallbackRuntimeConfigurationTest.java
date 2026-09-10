package com.chapchap.customer.global.config.customerai;

import com.chapchap.customer.domain.consultation.controller.summary.ConsultationSummaryCallbackController;
import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryCallbackOutcome;
import com.chapchap.customer.domain.consultation.service.summary.ConsultationSummaryCallbackStatePort;
import com.chapchap.customer.domain.knowledge.controller.processing.async.KnowledgeProcessingCallbackController;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingCallbackOutcome;
import com.chapchap.customer.domain.knowledge.service.processing.async.KnowledgeProcessingCallbackStatePort;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiCallbackJwtVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerAiCallbackRuntimeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CustomerAiCallbackRuntimeConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void remainsDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CustomerAiCallbackJwtVerifier.class);
            assertThat(context).doesNotHaveBean(KnowledgeProcessingCallbackController.class);
            assertThat(context).doesNotHaveBean(ConsultationSummaryCallbackController.class);
        });
    }

    @Test
    void createsAuthenticatedControllersOnlyWhenBothStatePortsExist() {
        runner.withPropertyValues("customer.ai.callback-auth.enabled=true")
                .withBean(KnowledgeProcessingCallbackStatePort.class,
                        () -> (headers, callback) -> KnowledgeProcessingCallbackOutcome.APPLIED_COMPLETED)
                .withBean(ConsultationSummaryCallbackStatePort.class,
                        () -> (headers, callback) -> ConsultationSummaryCallbackOutcome.APPLIED_COMPLETED)
                .run(context -> {
                    assertThat(context).hasSingleBean(CustomerAiCallbackJwtVerifier.class);
                    assertThat(context).hasSingleBean(KnowledgeProcessingCallbackController.class);
                    assertThat(context).hasSingleBean(ConsultationSummaryCallbackController.class);
                });
    }

    @Test
    void rejectsHttpJwksAndContractOverride() {
        runner.withPropertyValues(
                        "customer.ai.callback-auth.enabled=true",
                        "customer.ai.callback-auth.jwks-url=http://auth-service/.well-known/jwks.json")
                .run(context -> assertThat(context).hasFailed());

        runner.withPropertyValues(
                        "customer.ai.callback-auth.enabled=true",
                        "customer.ai.callback-auth.subject=untrusted-service")
                .run(context -> assertThat(context).hasFailed());
    }
}
