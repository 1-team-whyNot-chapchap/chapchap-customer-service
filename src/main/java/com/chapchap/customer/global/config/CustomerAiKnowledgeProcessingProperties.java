package com.chapchap.customer.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "customer.ai.knowledge-processing")
public class CustomerAiKnowledgeProcessingProperties {
    private String baseUrl = "http://localhost:8085";
    private String serviceToken = "";
    private int connectTimeoutMilliseconds = 1_000;
    private int readTimeoutMilliseconds = 10_000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public int getConnectTimeoutMilliseconds() {
        return connectTimeoutMilliseconds;
    }

    public void setConnectTimeoutMilliseconds(int connectTimeoutMilliseconds) {
        this.connectTimeoutMilliseconds = connectTimeoutMilliseconds;
    }

    public int getReadTimeoutMilliseconds() {
        return readTimeoutMilliseconds;
    }

    public void setReadTimeoutMilliseconds(int readTimeoutMilliseconds) {
        this.readTimeoutMilliseconds = readTimeoutMilliseconds;
    }
}
