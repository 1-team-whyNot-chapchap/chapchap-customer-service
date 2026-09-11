package com.chapchap.customer.global.config.customerai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "customer.ai.callback-auth")
public class CustomerAiCallbackAuthProperties {
    private boolean enabled;
    private String jwksUrl = "https://auth-service/.well-known/jwks.json";
    private String issuer = "chapchap-auth-service";
    private String audience = "chapchap-customer-service";
    private String subject = "customer-ai";
    private String scope = "customer-ai.callback";
    private int maximumLifetimeSeconds = 300;
    private int jwksCacheSeconds = 300;
    private int connectTimeoutMilliseconds = 1_000;
    private int readTimeoutMilliseconds = 3_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJwksUrl() {
        return jwksUrl;
    }

    public void setJwksUrl(String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public int getMaximumLifetimeSeconds() {
        return maximumLifetimeSeconds;
    }

    public void setMaximumLifetimeSeconds(int maximumLifetimeSeconds) {
        this.maximumLifetimeSeconds = maximumLifetimeSeconds;
    }

    public int getJwksCacheSeconds() {
        return jwksCacheSeconds;
    }

    public void setJwksCacheSeconds(int jwksCacheSeconds) {
        this.jwksCacheSeconds = jwksCacheSeconds;
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
