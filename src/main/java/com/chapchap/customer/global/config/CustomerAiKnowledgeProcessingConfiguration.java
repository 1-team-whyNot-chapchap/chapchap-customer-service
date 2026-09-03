package com.chapchap.customer.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomerAiKnowledgeProcessingProperties.class)
public class CustomerAiKnowledgeProcessingConfiguration {
    @Bean
    RestClient customerAiKnowledgeProcessingRestClient(CustomerAiKnowledgeProcessingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMilliseconds());
        requestFactory.setReadTimeout(properties.getReadTimeoutMilliseconds());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
