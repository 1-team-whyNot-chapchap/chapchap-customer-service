package com.chapchap.customer.global.config;

import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticSink;
import com.chapchap.customer.global.observability.customerai.CustomerAiTraceIdProvider;
import com.chapchap.customer.global.observability.customerai.MdcCustomerAiTraceIdProvider;
import com.chapchap.customer.global.observability.customerai.MeteredLoggingCustomerAiDiagnosticSink;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "customer.ai.observability",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CustomerAiObservabilityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CustomerAiDiagnosticSink customerAiDiagnosticSink(MeterRegistry meterRegistry) {
        return new MeteredLoggingCustomerAiDiagnosticSink(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    CustomerAiTraceIdProvider customerAiTraceIdProvider() {
        return new MdcCustomerAiTraceIdProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    CustomerAiDiagnosticPublisher customerAiDiagnosticPublisher(
            CustomerAiDiagnosticSink sink,
            CustomerAiTraceIdProvider traceIdProvider
    ) {
        return new CustomerAiDiagnosticPublisher(sink, traceIdProvider);
    }
}
