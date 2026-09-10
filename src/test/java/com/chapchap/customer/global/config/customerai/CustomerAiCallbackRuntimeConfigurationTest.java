package com.chapchap.customer.global.config.customerai;

import com.chapchap.customer.domain.consultation.controller.summary.ConsultationSummaryCallbackController;
import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryCallbackOutcome;
import com.chapchap.customer.domain.consultation.service.summary.ConsultationSummaryCallbackStatePort;
import com.chapchap.customer.domain.knowledge.controller.processing.async.KnowledgeProcessingCallbackController;
import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingCallbackOutcome;
import com.chapchap.customer.domain.knowledge.service.processing.async.KnowledgeProcessingCallbackStatePort;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiCallbackJwtVerifier;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiServiceTokenProvider;
import com.chapchap.customer.domain.consultation.service.summary.HttpCustomerAiConsultationSummaryClient;
import com.chapchap.customer.domain.knowledge.service.processing.async.HttpCustomerAiKnowledgeJobClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Test
    void academyHttpExceptionsBindAndCreateAuthenticatedClients() {
        runner.withUserConfiguration(CustomerAiKnowledgeProcessingConfiguration.class, LateTokenConfiguration.class)
                .withPropertyValues("customer.ai.callback-auth.enabled=true",
                        "customer.ai.callback-auth.jwks-url=http://auth-service/.well-known/jwks.json",
                        "customer.ai.transport.http-allowed-origins=http://auth-service:80,http://customer-ai-service:8085",
                        "customer.ai.knowledge-processing.async-enabled=true",
                        "customer.ai.consultation-summary.async-enabled=true",
                        "customer.ai.knowledge-processing.base-url=http://customer-ai-service:8085")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CustomerAiCallbackJwtVerifier.class);
                    assertThat(context).hasSingleBean(HttpCustomerAiKnowledgeJobClient.class);
                    assertThat(context).hasSingleBean(HttpCustomerAiConsultationSummaryClient.class);
                });
    }

    @Test
    void enabledClientsResolveTokenProviderRegisteredByLaterConfiguration() {
        runner.withUserConfiguration(CustomerAiKnowledgeProcessingConfiguration.class, LateTokenConfiguration.class)
                .withPropertyValues("customer.ai.callback-auth.enabled=true",
                        "customer.ai.knowledge-processing.async-enabled=true",
                        "customer.ai.consultation-summary.async-enabled=true",
                        "customer.ai.knowledge-processing.base-url=https://localhost:8445")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HttpCustomerAiKnowledgeJobClient.class);
                    assertThat(context).hasSingleBean(HttpCustomerAiConsultationSummaryClient.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class LateTokenConfiguration {
        @Bean
        CustomerAiServiceTokenProvider lateTokenProvider() {
            return () -> { throw new IllegalStateException("No external calls in configuration test"); };
        }
    }
}
