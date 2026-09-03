package com.chapchap.customer.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "customer.storage.knowledge")
public class KnowledgeStorageProperties {
    private String endpoint = "http://localhost:9000";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "customer-knowledge";
    private int presignedGetUrlExpirySeconds = 300;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public int getPresignedGetUrlExpirySeconds() {
        return presignedGetUrlExpirySeconds;
    }

    public void setPresignedGetUrlExpirySeconds(int presignedGetUrlExpirySeconds) {
        this.presignedGetUrlExpirySeconds = presignedGetUrlExpirySeconds;
    }
}
