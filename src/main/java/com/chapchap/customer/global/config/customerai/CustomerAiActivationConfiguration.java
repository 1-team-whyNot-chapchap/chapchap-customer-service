package com.chapchap.customer.global.config.customerai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class CustomerAiActivationConfiguration {

    @Bean
    CustomerAiRuntimeActivationGate customerAiRuntimeActivationGate(
            Environment environment,
            ObjectMapper objectMapper
    ) {
        return new CustomerAiRuntimeActivationGate(environment, objectMapper);
    }
}
