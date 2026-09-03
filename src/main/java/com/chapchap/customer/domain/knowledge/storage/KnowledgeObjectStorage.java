package com.chapchap.customer.domain.knowledge.storage;

import java.io.InputStream;

public interface KnowledgeObjectStorage {
    void store(String objectKey, InputStream content, long contentLength, String contentType);

    void delete(String objectKey);

    String createPresignedGetUrl(String objectKey);
}
