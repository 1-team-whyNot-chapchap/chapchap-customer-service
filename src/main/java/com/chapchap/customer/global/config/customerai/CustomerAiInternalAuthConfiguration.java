package com.chapchap.customer.global.config.customerai;

import com.chapchap.customer.domain.customerai.service.security.AuthServiceCustomerAiServiceTokenProvider;
import com.chapchap.customer.global.exception.customerai.security.CustomerAiAuthenticationUnavailableException;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiRequestCredentialsProvider;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiServiceTokenProvider;
import com.chapchap.customer.domain.customerai.response.security.CustomerAiSubjectJwksDocument;
import com.chapchap.customer.domain.customerai.dto.security.CustomerAiSubjectKeyMaterial;
import com.chapchap.customer.domain.customerai.service.security.Rs256CustomerAiSubjectAssertionIssuer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomerAiInternalAuthProperties.class)
public class CustomerAiInternalAuthConfiguration {
    @Bean("customerAiAuthRestClient")
    @ConditionalOnProperty(prefix = "customer.ai.internal-auth", name = "enabled", havingValue = "true")
    RestClient customerAiAuthRestClient(CustomerAiInternalAuthProperties properties) {
        validateHttpsOrigin(properties.getAuthBaseUrl());
        requirePositive(properties.getConnectTimeoutMilliseconds(), "connect timeout");
        requirePositive(properties.getReadTimeoutMilliseconds(), "read timeout");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMilliseconds());
        requestFactory.setReadTimeout(properties.getReadTimeoutMilliseconds());
        return RestClient.builder()
                .baseUrl(properties.getAuthBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "customer.ai.internal-auth", name = "enabled", havingValue = "true")
    CustomerAiSubjectKeyMaterial customerAiSubjectKeyMaterial(CustomerAiInternalAuthProperties properties) {
        return CustomerAiSubjectKeyMaterial.fromPem(
                properties.getSubjectPrivateKeyPem(), properties.getSubjectPublicKeyPem());
    }

    @Bean
    @ConditionalOnProperty(prefix = "customer.ai.internal-auth", name = "enabled", havingValue = "true")
    CustomerAiServiceTokenProvider authServiceCustomerAiServiceTokenProvider(
            @Qualifier("customerAiAuthRestClient") RestClient customerAiAuthRestClient,
            CustomerAiInternalAuthProperties properties
    ) {
        return new AuthServiceCustomerAiServiceTokenProvider(
                customerAiAuthRestClient,
                properties.getTokenPath(),
                properties.getClientId(),
                properties.getClientSecret(),
                properties.getAudience(),
                properties.getScope());
    }

    @Bean
    @ConditionalOnMissingBean(CustomerAiServiceTokenProvider.class)
    CustomerAiServiceTokenProvider unavailableCustomerAiServiceTokenProvider() {
        return () -> {
            throw new CustomerAiAuthenticationUnavailableException();
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "customer.ai.internal-auth", name = "enabled", havingValue = "true")
    Rs256CustomerAiSubjectAssertionIssuer customerAiSubjectAssertionIssuer(
            CustomerAiSubjectKeyMaterial keyMaterial,
            CustomerAiInternalAuthProperties properties
    ) {
        return new Rs256CustomerAiSubjectAssertionIssuer(
                keyMaterial.privateKey(),
                properties.getSubjectKeyId(),
                Duration.ofSeconds(properties.getSubjectLifetimeSeconds()),
                Clock.system(ZoneId.of("Asia/Seoul")));
    }

    @Bean
    @ConditionalOnProperty(prefix = "customer.ai.internal-auth", name = "enabled", havingValue = "true")
    CustomerAiRequestCredentialsProvider customerAiRequestCredentialsProvider(
            CustomerAiServiceTokenProvider tokenProvider,
            Rs256CustomerAiSubjectAssertionIssuer assertionIssuer
    ) {
        return new CustomerAiRequestCredentialsProvider(tokenProvider, assertionIssuer);
    }

    @Bean
    @ConditionalOnProperty(prefix = "customer.ai.internal-auth", name = "enabled", havingValue = "true")
    CustomerAiSubjectJwksDocument customerAiSubjectJwksDocument(
            CustomerAiSubjectKeyMaterial keyMaterial,
            CustomerAiInternalAuthProperties properties
    ) {
        return CustomerAiSubjectJwksDocument.from(properties.getSubjectKeyId(), keyMaterial.publicKey());
    }

    private static void validateHttpsOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalStateException("Customer-AI Auth base URL must be an HTTPS origin.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Customer-AI Auth base URL must be an HTTPS origin.");
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalStateException("Customer-AI Auth " + field + " must be positive.");
        }
    }
}
