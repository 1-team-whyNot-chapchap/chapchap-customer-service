package com.chapchap.customer.global.config;

import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticSink;
import com.chapchap.customer.global.observability.customerai.CustomerAiTraceIdProvider;
import com.chapchap.customer.global.observability.customerai.MeteredLoggingCustomerAiDiagnosticSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerAiObservabilityConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withUserConfiguration(CustomerAiObservabilityConfiguration.class);

    @Test
    void registersRealObservabilityBackendByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CustomerAiDiagnosticSink.class);
            assertThat(context).hasSingleBean(CustomerAiTraceIdProvider.class);
            assertThat(context).hasSingleBean(CustomerAiDiagnosticPublisher.class);
            assertThat(context.getBean(CustomerAiDiagnosticSink.class))
                    .isInstanceOf(MeteredLoggingCustomerAiDiagnosticSink.class);
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
