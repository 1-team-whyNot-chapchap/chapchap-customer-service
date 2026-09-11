package com.chapchap.customer.global.config.customerai;

import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticSink;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiTraceIdProvider;
import com.chapchap.customer.domain.customerai.service.observability.MdcCustomerAiTraceIdProvider;
import com.chapchap.customer.domain.customerai.service.observability.LoggingCustomerAiDiagnosticSink;

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
    CustomerAiDiagnosticSink customerAiDiagnosticSink() {
        return new LoggingCustomerAiDiagnosticSink();
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
