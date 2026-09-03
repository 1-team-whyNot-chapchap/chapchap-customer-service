package com.chapchap.customer.global.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class QualityInquiryStorageConfiguration {
    @Bean("qualityInquiryMinioClient")
    MinioClient qualityInquiryMinioClient(QualityInquiryStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
