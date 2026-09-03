package com.chapchap.customer.domain.quality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "quality_inquiry_attachments")
public class QualityInquiryAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "quality_inquiry_id", nullable = false)
    private QualityInquiry qualityInquiry;

    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected QualityInquiryAttachment() {
    }

    private QualityInquiryAttachment(
            QualityInquiry qualityInquiry,
            String objectKey,
            String originalFilename,
            String contentType,
            long fileSize,
            LocalDateTime createdAt
    ) {
        this.qualityInquiry = qualityInquiry;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
    }

    public static QualityInquiryAttachment create(
            QualityInquiry qualityInquiry,
            String objectKey,
            String originalFilename,
            String contentType,
            long fileSize,
            LocalDateTime createdAt
    ) {
        return new QualityInquiryAttachment(qualityInquiry, objectKey, originalFilename, contentType, fileSize, createdAt);
    }

    public Long getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
