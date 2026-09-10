package com.chapchap.customer.global.config.customerai;

import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticSink;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiTraceIdProvider;
import com.chapchap.customer.domain.customerai.service.observability.LoggingCustomerAiDiagnosticSink;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerAiObservabilityConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()

            .withUserConfiguration(CustomerAiObservabilityConfiguration.class);

    @Test
    void registersRealObservabilityBackendByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CustomerAiDiagnosticSink.class);
            assertThat(context).hasSingleBean(CustomerAiTraceIdProvider.class);
            assertThat(context).hasSingleBean(CustomerAiDiagnosticPublisher.class);
            assertThat(context.getBean(CustomerAiDiagnosticSink.class))
                    .isInstanceOf(LoggingCustomerAiDiagnosticSink.class);
        });
    }

    @Test
    void allowsExplicitDisableWithoutFallbackBackend() {
        contextRunner.withPropertyValues("customer.ai.observability.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CustomerAiDiagnosticSink.class);
                    assertThat(context).doesNotHaveBean(CustomerAiTraceIdProvider.class);
                    assertThat(context).doesNotHaveBean(CustomerAiDiagnosticPublisher.class);
                });
    }
}
