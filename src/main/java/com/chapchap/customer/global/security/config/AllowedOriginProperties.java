package com.chapchap.customer.global.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record AllowedOriginProperties(List<String> allowedOrigins) {
    public boolean contains(String origin) {
        return allowedOrigins != null && allowedOrigins.stream().anyMatch(origin::equals);
    }
}
