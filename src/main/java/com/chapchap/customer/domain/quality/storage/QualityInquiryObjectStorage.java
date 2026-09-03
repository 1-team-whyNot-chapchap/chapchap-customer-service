package com.chapchap.customer.domain.quality.storage;

import java.io.InputStream;

public interface QualityInquiryObjectStorage {
    void store(String objectKey, InputStream content, long contentLength, String contentType);

    void delete(String objectKey);
}
