package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ConsultationSummaryCallbackFingerprint {
    private ConsultationSummaryCallbackFingerprint() {
    }

    static String create(ConsultationSummaryCallback callback) {
        String canonical = String.join("|",
                Long.toString(callback.summaryJobId()),
                Long.toString(callback.consultationId()),
                callback.status().name(),
                value(callback.summary()),
                callback.failureCode() == null ? "" : callback.failureCode().name(),
                value(callback.retryable()));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
