package com.chapchap.customer.domain.quality.service;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.quality.entity.QualityInquiry;
import com.chapchap.customer.domain.quality.file.QualityInquiryFileValidator;
import com.chapchap.customer.domain.quality.file.ValidatedQualityInquiryFile;
import com.chapchap.customer.domain.quality.repository.QualityInquiryRepository;
import com.chapchap.customer.domain.quality.request.QualityInquiryCreateRequest;
import com.chapchap.customer.domain.quality.request.QualityInquiryProcessRequest;
import com.chapchap.customer.domain.quality.response.QualityInquiryResponse;
import com.chapchap.customer.domain.quality.response.QualityInquirySummaryResponse;
import com.chapchap.customer.domain.quality.storage.QualityInquiryObjectStorage;
import com.chapchap.customer.global.error.custom.quality.QualityInquiryNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QualityInquiryService {
    private final QualityInquiryRepository qualityInquiryRepository;
    private final QualityInquiryPersistenceService persistenceService;
    private final QualityInquiryFileValidator fileValidator;
    private final QualityInquiryObjectStorage objectStorage;
    private final AuditLogWriter auditLogWriter;

    public QualityInquiryResponse create(Long userId, QualityInquiryCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        QualityInquiry inquiry = QualityInquiry.create(
                userId,
                request.getOrderId(),
                request.getProductId(),
                normalize(request.getDeliveryId()),
                request.getInquiryType(),
                request.getContent().trim(),
                now
        );

        List<ValidatedQualityInquiryFile> files = request.getAttachments().stream()
                .filter(file -> !file.isEmpty())
                .map(fileValidator::validate)
                .toList();
        List<String> storedObjectKeys = new ArrayList<>();

        try {
            for (ValidatedQualityInquiryFile file : files) {
                String objectKey = "quality-inquiries/" + UUID.randomUUID();
                objectStorage.store(
                        objectKey,
                        new ByteArrayInputStream(file.content()),
                        file.size(),
                        file.contentType()
                );
                storedObjectKeys.add(objectKey);
                inquiry.addAttachment(objectKey, file.originalFilename(), file.contentType(), file.size(), now);
            }
            return QualityInquiryResponse.from(persistenceService.persist(inquiry));
        } catch (RuntimeException exception) {
            cleanupStoredObjects(storedObjectKeys, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<QualityInquirySummaryResponse> findMyInquiries(Long userId) {
        return qualityInquiryRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(QualityInquirySummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QualityInquiryResponse findMyInquiry(Long userId, Long qualityInquiryId) {
        return QualityInquiryResponse.from(qualityInquiryRepository.findByIdAndUserId(qualityInquiryId, userId)
                .orElseThrow(QualityInquiryNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<QualityInquirySummaryResponse> findAllInquiries() {
        return qualityInquiryRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(QualityInquirySummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QualityInquiryResponse findInquiry(Long qualityInquiryId) {
        return QualityInquiryResponse.from(findInquiryEntity(qualityInquiryId));
    }

    @Transactional
    public QualityInquiryResponse process(Long actorUserId, Long qualityInquiryId, QualityInquiryProcessRequest request) {
        QualityInquiry inquiry = findInquiryEntity(qualityInquiryId);
        QualityInquiry before = inquiry.snapshot();
        LocalDateTime now = LocalDateTime.now();
        inquiry.process(
                actorUserId,
                request.inquiryType(),
                request.priority(),
                request.status(),
                request.adminAnswer(),
                now
        );
        auditLogWriter.recordQualityInquiryProcessed(actorUserId, before, inquiry, now);
        return QualityInquiryResponse.from(inquiry);
    }

    private QualityInquiry findInquiryEntity(Long qualityInquiryId) {
        return qualityInquiryRepository.findById(qualityInquiryId)
                .orElseThrow(QualityInquiryNotFoundException::new);
    }

    private void cleanupStoredObjects(List<String> storedObjectKeys, RuntimeException originalException) {
        for (String objectKey : storedObjectKeys) {
            try {
                objectStorage.delete(objectKey);
            } catch (RuntimeException cleanupException) {
                originalException.addSuppressed(cleanupException);
            }
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
