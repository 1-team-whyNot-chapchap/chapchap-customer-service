package com.chapchap.customer.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "customer.ai.internal-auth")
public class CustomerAiInternalAuthProperties {
    private boolean enabled;
    private String authBaseUrl = "https://auth-service";
    private String tokenPath = "/internal/v1/service-tokens";
    private String clientId = "customer-service";
    private String clientSecret = "";
    private String audience = "chapchap-customer-ai";
    private String scope = "customer-ai.invoke";
    private String subjectKeyId = "customer-subject-1";
    private String subjectPrivateKeyPem = "";
    private String subjectPublicKeyPem = "";
    private int subjectLifetimeSeconds = 60;
    private int connectTimeoutMilliseconds = 1_000;
    private int readTimeoutMilliseconds = 3_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
    }

    public String getTokenPath() {
        return tokenPath;
    }

    public void setTokenPath(String tokenPath) {
        this.tokenPath = tokenPath;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getSubjectKeyId() {
        return subjectKeyId;
    }

    public void setSubjectKeyId(String subjectKeyId) {
        this.subjectKeyId = subjectKeyId;
    }

    public String getSubjectPrivateKeyPem() {
        return subjectPrivateKeyPem;
    }

    public void setSubjectPrivateKeyPem(String subjectPrivateKeyPem) {
        this.subjectPrivateKeyPem = subjectPrivateKeyPem;
    }

    public String getSubjectPublicKeyPem() {
        return subjectPublicKeyPem;
    }

    public void setSubjectPublicKeyPem(String subjectPublicKeyPem) {
        this.subjectPublicKeyPem = subjectPublicKeyPem;
    }

    public int getSubjectLifetimeSeconds() {
        return subjectLifetimeSeconds;
    }

    public void setSubjectLifetimeSeconds(int subjectLifetimeSeconds) {
        this.subjectLifetimeSeconds = subjectLifetimeSeconds;
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
