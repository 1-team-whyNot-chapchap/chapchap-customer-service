package com.chapchap.customer.global.config.customerai;

import com.chapchap.customer.domain.consultation.service.summary.ConsultationSummaryCallbackConsumer;
import com.chapchap.customer.domain.consultation.controller.summary.ConsultationSummaryCallbackController;
import com.chapchap.customer.domain.consultation.service.summary.ConsultationSummaryCallbackParser;
import com.chapchap.customer.domain.consultation.service.summary.ConsultationSummaryCallbackStatePort;
import com.chapchap.customer.domain.consultation.service.summary.CustomerAiConsultationSummaryResponseParser;
import com.chapchap.customer.domain.consultation.service.summary.HttpCustomerAiConsultationSummaryClient;
import com.chapchap.customer.domain.knowledge.service.processing.async.KnowledgeProcessingCallbackConsumer;
import com.chapchap.customer.domain.knowledge.controller.processing.async.KnowledgeProcessingCallbackController;
import com.chapchap.customer.domain.knowledge.service.processing.async.KnowledgeProcessingCallbackParser;
import com.chapchap.customer.domain.knowledge.service.processing.async.KnowledgeProcessingCallbackStatePort;
import com.chapchap.customer.domain.knowledge.service.processing.async.CustomerAiKnowledgeJobResponseParser;
import com.chapchap.customer.domain.knowledge.service.processing.async.HttpCustomerAiKnowledgeJobClient;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiCallbackJwtVerifier;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiCallbackVerificationKeyResolver;
import com.chapchap.customer.domain.customerai.service.security.CustomerAiServiceTokenProvider;
import com.chapchap.customer.domain.customerai.service.security.RemoteAuthJwksKeyResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomerAiCallbackAuthProperties.class)
@ConditionalOnProperty(prefix = "customer.ai.callback-auth", name = "enabled", havingValue = "true")
public class CustomerAiCallbackRuntimeConfiguration {
    @org.springframework.beans.factory.annotation.Value("${customer.ai.transport.http-allowed-origins:}")
    private String httpAllowedOrigins = "";

    private static final String REQUIRED_ISSUER = "chapchap-auth-service";
    private static final String REQUIRED_AUDIENCE = "chapchap-customer-service";
    private static final String REQUIRED_SUBJECT = "customer-ai";
    private static final String REQUIRED_SCOPE = "customer-ai.callback";

    @Bean
    CustomerAiCallbackVerificationKeyResolver customerAiCallbackVerificationKeyResolver(
            CustomerAiCallbackAuthProperties properties,
            ObjectMapper objectMapper
    ) {
        validateProperties(properties);
        SimpleClientHttpRequestFactory requestFactory = new NoRedirectClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMilliseconds());
        requestFactory.setReadTimeout(properties.getReadTimeoutMilliseconds());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getJwksUrl())
                .requestFactory(requestFactory)
                .build();
        return new RemoteAuthJwksKeyResolver(
                restClient,
                objectMapper,
                Duration.ofSeconds(properties.getJwksCacheSeconds()),
                callbackClock());
    }

    @Bean
    CustomerAiCallbackJwtVerifier customerAiCallbackJwtVerifier(
            CustomerAiCallbackVerificationKeyResolver keyResolver,
            ObjectMapper objectMapper,
            CustomerAiCallbackAuthProperties properties
    ) {
        return new CustomerAiCallbackJwtVerifier(
                keyResolver,
                objectMapper,
                properties.getIssuer(),
                properties.getAudience(),
                properties.getSubject(),
                properties.getScope(),
                Duration.ofSeconds(properties.getMaximumLifetimeSeconds()),
                callbackClock());
    }

    @Bean
    @ConditionalOnProperty(prefix = "customer.ai.knowledge-processing", name = "async-enabled", havingValue = "true")
    HttpCustomerAiKnowledgeJobClient httpCustomerAiKnowledgeJobClient(
            @Qualifier("customerAiKnowledgeProcessingRestClient") RestClient restClient,
            CustomerAiServiceTokenProvider tokenProvider,
            CustomerAiKnowledgeProcessingProperties processingProperties,
            ObjectMapper objectMapper,
            ObjectProvider<CustomerAiDiagnosticPublisher> diagnostics
    ) {
        validateHttpsOrigin(processingProperties.getBaseUrl());
        return new HttpCustomerAiKnowledgeJobClient(
                restClient,
                tokenProvider,
                new CustomerAiKnowledgeJobResponseParser(objectMapper),
                diagnostics.getIfAvailable(CustomerAiDiagnosticPublisher::noOp));
    }

    @Bean
    @ConditionalOnProperty(prefix = "customer.ai.consultation-summary", name = "async-enabled", havingValue = "true")
    HttpCustomerAiConsultationSummaryClient httpCustomerAiConsultationSummaryClient(
            @Qualifier("customerAiKnowledgeProcessingRestClient") RestClient restClient,
            CustomerAiServiceTokenProvider tokenProvider,
            CustomerAiKnowledgeProcessingProperties processingProperties,
            ObjectMapper objectMapper,
            ObjectProvider<CustomerAiDiagnosticPublisher> diagnostics
    ) {
        validateHttpsOrigin(processingProperties.getBaseUrl());
        return new HttpCustomerAiConsultationSummaryClient(
                restClient,
                tokenProvider,
                new CustomerAiConsultationSummaryResponseParser(objectMapper),
                diagnostics.getIfAvailable(CustomerAiDiagnosticPublisher::noOp));
    }

    @Bean
    @ConditionalOnBean(KnowledgeProcessingCallbackStatePort.class)
    KnowledgeProcessingCallbackConsumer knowledgeProcessingCallbackConsumer(
            ObjectMapper objectMapper,
            KnowledgeProcessingCallbackStatePort statePort,
            ObjectProvider<CustomerAiDiagnosticPublisher> diagnostics
    ) {
        return new KnowledgeProcessingCallbackConsumer(
                new KnowledgeProcessingCallbackParser(objectMapper),
                statePort,
                diagnostics.getIfAvailable(CustomerAiDiagnosticPublisher::noOp));
    }

    @Bean
    @ConditionalOnBean(ConsultationSummaryCallbackStatePort.class)
    ConsultationSummaryCallbackConsumer consultationSummaryCallbackConsumer(
            ObjectMapper objectMapper,
            ConsultationSummaryCallbackStatePort statePort,
            ObjectProvider<CustomerAiDiagnosticPublisher> diagnostics
    ) {
        return new ConsultationSummaryCallbackConsumer(
                new ConsultationSummaryCallbackParser(objectMapper),
                statePort,
                diagnostics.getIfAvailable(CustomerAiDiagnosticPublisher::noOp));
    }

    @Bean
    @ConditionalOnBean(KnowledgeProcessingCallbackConsumer.class)
    @ConditionalOnMissingBean(KnowledgeProcessingCallbackController.class)
    KnowledgeProcessingCallbackController knowledgeProcessingCallbackController(
            CustomerAiCallbackJwtVerifier verifier,
            KnowledgeProcessingCallbackConsumer consumer
    ) {
        return new KnowledgeProcessingCallbackController(verifier, consumer);
    }

    @Bean
    @ConditionalOnBean(ConsultationSummaryCallbackConsumer.class)
    @ConditionalOnMissingBean(ConsultationSummaryCallbackController.class)
    ConsultationSummaryCallbackController consultationSummaryCallbackController(
            CustomerAiCallbackJwtVerifier verifier,
            ConsultationSummaryCallbackConsumer consumer
    ) {
        return new ConsultationSummaryCallbackController(verifier, consumer);
    }

    private Clock callbackClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    private void validateProperties(CustomerAiCallbackAuthProperties properties) {
        validateHttpsJwksUrl(properties.getJwksUrl());
        requireFixed(properties.getIssuer(), REQUIRED_ISSUER, "issuer");
        requireFixed(properties.getAudience(), REQUIRED_AUDIENCE, "audience");
        requireFixed(properties.getSubject(), REQUIRED_SUBJECT, "subject");
        requireFixed(properties.getScope(), REQUIRED_SCOPE, "scope");
        requireRange(properties.getMaximumLifetimeSeconds(), 1, 300, "maximum lifetime");
        requireRange(properties.getJwksCacheSeconds(), 60, 3_600, "JWKS cache");
        requireRange(properties.getConnectTimeoutMilliseconds(), 1, 10_000, "connect timeout");
        requireRange(properties.getReadTimeoutMilliseconds(), 1, 10_000, "read timeout");
    }

    private void validateHttpsJwksUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!CustomerAiTransportPolicy.allows(uri, httpAllowedOrigins)
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || uri.getPath() == null
                    || uri.getPath().isBlank()
                    || "/".equals(uri.getPath())) {
                throw new IllegalStateException("Customer-AI callback JWKS URL must be HTTPS.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Customer-AI callback JWKS URL must be HTTPS.");
        }
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
                throw new IllegalStateException("Customer-AI base URL must be an HTTPS origin.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Customer-AI base URL must be an HTTPS origin.");
        }
    }

    private void requireFixed(String actual, String expected, String field) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Customer-AI callback " + field + " contract is not allowed.");
        }
    }

    private void requireRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalStateException("Customer-AI callback " + field + " is out of range.");
        }
    }
}
