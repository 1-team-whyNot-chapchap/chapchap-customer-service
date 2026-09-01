package com.chapchap.customer.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sub-service")
public record SubServiceUriConfig(
    String frontendCallbackUri
) {

}
