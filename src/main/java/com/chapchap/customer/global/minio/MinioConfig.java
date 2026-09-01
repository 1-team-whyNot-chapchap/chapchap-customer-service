package com.chapchap.customer.global.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "minio")
public record MinioConfig(
    String minioEndpoint,
    String minioBucket,
    String minioAccessKey,
    String minioSecretKey,
    String minioProfilePath,
    List<String> allowImageExtensions
) {

}
