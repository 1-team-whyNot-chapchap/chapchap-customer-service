package com.chapchap.customer.domain.knowledge.storage;

import com.chapchap.customer.global.config.KnowledgeStorageProperties;
import com.chapchap.customer.global.error.custom.knowledge.KnowledgeStorageException;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class MinioKnowledgeObjectStorage implements KnowledgeObjectStorage {
    private final MinioClient knowledgeMinioClient;
    private final KnowledgeStorageProperties properties;

    @Override
    public void store(String objectKey, InputStream content, long contentLength, String contentType) {
        try {
            knowledgeMinioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(content, contentLength, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw new KnowledgeStorageException("Knowledge 원본을 저장하지 못했습니다.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            knowledgeMinioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new KnowledgeStorageException("Knowledge 원본을 정리하지 못했습니다.", exception);
        }
    }

    @Override
    public String createPresignedGetUrl(String objectKey) {
        try {
            return knowledgeMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .expiry(properties.getPresignedGetUrlExpirySeconds())
                    .build());
        } catch (Exception exception) {
            throw new KnowledgeStorageException("Knowledge 원본 접근 URL을 만들지 못했습니다.", exception);
        }
    }
}
