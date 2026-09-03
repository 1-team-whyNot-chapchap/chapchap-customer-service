package com.chapchap.customer.domain.quality.storage;

import com.chapchap.customer.global.config.QualityInquiryStorageProperties;
import com.chapchap.customer.global.error.custom.quality.QualityInquiryStorageException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioQualityInquiryObjectStorage implements QualityInquiryObjectStorage {
    private final MinioClient minioClient;
    private final QualityInquiryStorageProperties properties;

    public MinioQualityInquiryObjectStorage(
            @Qualifier("qualityInquiryMinioClient") MinioClient minioClient,
            QualityInquiryStorageProperties properties
    ) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void store(String objectKey, InputStream content, long contentLength, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(content, contentLength, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw new QualityInquiryStorageException("품질 문의 첨부파일을 저장하지 못했습니다.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new QualityInquiryStorageException("품질 문의 첨부파일을 정리하지 못했습니다.", exception);
        }
    }
}
