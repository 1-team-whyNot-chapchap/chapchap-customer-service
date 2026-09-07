package com.chapchap.customer.global.config;

import com.chapchap.customer.domain.consultation.ai.CustomerAiConsultationClient;
import com.chapchap.customer.global.security.customerai.CustomerAiRequestCredentialsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CustomerAiConsultationResponseRuntimeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    CustomerAiConsultationResponseRuntimeConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(CustomerAiRequestCredentialsProvider.class,
                    () -> mock(CustomerAiRequestCredentialsProvider.class));

    @Test
    void remainsDisabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(CustomerAiConsultationClient.class));
    }

    @Test
    void createsClientOnlyForExplicitHttpsRuntime() {
        runner.withPropertyValues(
                        "customer.ai.consultation-response.async-enabled=true",
                        "customer.ai.consultation-response.base-url=https://customer-ai.internal")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CustomerAiConsultationClient.class);
                });
    }

    @Test
    void rejectsHttpOriginWhenRuntimeIsEnabled() {
        runner.withPropertyValues(
                        "customer.ai.consultation-response.async-enabled=true",
                        "customer.ai.consultation-response.base-url=http://customer-ai.internal")
                .run(context -> assertThat(context).hasFailed());
    }
}
