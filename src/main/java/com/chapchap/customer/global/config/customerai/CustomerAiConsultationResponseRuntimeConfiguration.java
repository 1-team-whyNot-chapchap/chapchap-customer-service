package com.chapchap.customer.global.config.customerai;

import com.chapchap.customer.domain.consultation.service.ai.CustomerAiConsultationClient;
import com.chapchap.customer.domain.consultation.service.ai.CustomerAiConsultationResponseParser;
import com.chapchap.customer.domain.consultation.service.ai.HttpCustomerAiConsultationClient;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiRequestCredentialsProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomerAiConsultationResponseProperties.class)
@ConditionalOnProperty(
        prefix = "customer.ai.consultation-response",
        name = "async-enabled",
        havingValue = "true"
)
public class CustomerAiConsultationResponseRuntimeConfiguration {
    @org.springframework.beans.factory.annotation.Value("${customer.ai.transport.http-allowed-origins:}")
    private String httpAllowedOrigins = "";

    @Bean
    CustomerAiConsultationClient customerAiConsultationClient(
            CustomerAiRequestCredentialsProvider credentialsProvider,
            CustomerAiConsultationResponseProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<CustomerAiDiagnosticPublisher> diagnostics
    ) {
        validateHttpsOrigin(properties.getBaseUrl());
        requirePositive(properties.getConnectTimeoutMilliseconds(), "connect timeout");
        requirePositive(properties.getReadTimeoutMilliseconds(), "read timeout");
        SimpleClientHttpRequestFactory requestFactory = new NoRedirectClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMilliseconds());
        requestFactory.setReadTimeout(properties.getReadTimeoutMilliseconds());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        return new HttpCustomerAiConsultationClient(
                restClient,
                credentialsProvider,
                new CustomerAiConsultationResponseParser(objectMapper),
                diagnostics.getIfAvailable(CustomerAiDiagnosticPublisher::noOp)
        );
    }

    private void validateHttpsOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (!CustomerAiTransportPolicy.allows(uri, httpAllowedOrigins)
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalStateException("Customer-AI base URL은 HTTPS origin이어야 합니다.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Customer-AI base URL은 HTTPS origin이어야 합니다.");
        }
    }

    private void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalStateException("Customer-AI consultation " + field + "은 양수여야 합니다.");
        }
    }
}
