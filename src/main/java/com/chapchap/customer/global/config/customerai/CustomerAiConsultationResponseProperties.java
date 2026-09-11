package com.chapchap.customer.global.config.customerai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "customer.ai.consultation-response")
public class CustomerAiConsultationResponseProperties {
    private boolean asyncEnabled;
    private String baseUrl = "http://localhost:8085";
    private int connectTimeoutMilliseconds = 1_000;
    private int readTimeoutMilliseconds = 10_000;

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
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
