package com.chapchap.customer.domain.quality.entity;

import com.chapchap.customer.global.error.custom.quality.QualityInquiryStateException;
import com.chapchap.customer.global.error.custom.quality.QualityInquiryValidationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quality_inquiries")
public class QualityInquiry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quality_inquiry_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "delivery_id", length = 64)
    private String deliveryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_type", nullable = false, length = 20)
    private QualityInquiryType inquiryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QualityInquiryStatus status;

    @Column(length = 20)
    private String priority;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Column(name = "admin_answer", columnDefinition = "TEXT")
    private String adminAnswer;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "qualityInquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<QualityInquiryAttachment> attachments = new ArrayList<>();

    protected QualityInquiry() {
    }

    private QualityInquiry(
            Long userId,
            Long orderId,
            Long productId,
            String deliveryId,
            QualityInquiryType inquiryType,
            String content,
            LocalDateTime now
    ) {
        validateReferenceIds(inquiryType, orderId, productId, deliveryId);
        this.userId = userId;
        this.orderId = orderId;
        this.productId = productId;
        this.deliveryId = deliveryId;
        this.inquiryType = inquiryType;
        this.status = QualityInquiryStatus.RECEIVED;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static QualityInquiry create(
            Long userId,
            Long orderId,
            Long productId,
            String deliveryId,
            QualityInquiryType inquiryType,
            String content,
            LocalDateTime now
    ) {
        return new QualityInquiry(userId, orderId, productId, deliveryId, inquiryType, content, now);
    }

    public void addAttachment(String objectKey, String originalFilename, String contentType, long fileSize, LocalDateTime now) {
        attachments.add(QualityInquiryAttachment.create(this, objectKey, originalFilename, contentType, fileSize, now));
    }

    public void process(
            Long actorUserId,
            QualityInquiryType nextInquiryType,
            String nextPriority,
            QualityInquiryStatus nextStatus,
            String nextAdminAnswer,
            LocalDateTime now
    ) {
        validateReferenceIds(nextInquiryType, orderId, productId, deliveryId);
        if (!status.canTransitionTo(nextStatus)) {
            throw new QualityInquiryStateException("품질 문의 상태를 다음 단계로만 변경할 수 있습니다.");
        }
        String normalizedAnswer = normalize(nextAdminAnswer);
        String effectiveAnswer = normalizedAnswer == null ? adminAnswer : normalizedAnswer;
        if (nextStatus == QualityInquiryStatus.RESOLVED && (effectiveAnswer == null || effectiveAnswer.isBlank())) {
            throw new QualityInquiryValidationException("처리 완료에는 관리자 답변이 필요합니다.");
        }

        inquiryType = nextInquiryType;
        priority = normalize(nextPriority);
        status = nextStatus;
        adminAnswer = effectiveAnswer;
        assignedAdminId = actorUserId;
        if (nextStatus == QualityInquiryStatus.RESOLVED) {
            resolvedAt = now;
        }
        if (nextStatus == QualityInquiryStatus.CLOSED) {
            closedAt = now;
        }
        updatedAt = now;
    }

    public QualityInquiry snapshot() {
        QualityInquiry snapshot = new QualityInquiry();
        snapshot.id = id;
        snapshot.userId = userId;
        snapshot.orderId = orderId;
        snapshot.productId = productId;
        snapshot.deliveryId = deliveryId;
        snapshot.inquiryType = inquiryType;
        snapshot.status = status;
        snapshot.priority = priority;
        snapshot.content = content;
        snapshot.summary = summary;
        snapshot.assignedAdminId = assignedAdminId;
        snapshot.adminAnswer = adminAnswer;
        snapshot.resolvedAt = resolvedAt;
        snapshot.closedAt = closedAt;
        snapshot.createdAt = createdAt;
        snapshot.updatedAt = updatedAt;
        return snapshot;
    }

    private void validateReferenceIds(
            QualityInquiryType inquiryType,
            Long orderId,
            Long productId,
            String deliveryId
    ) {
        if (inquiryType == null) {
            throw new QualityInquiryValidationException("품질 문의 유형은 필수입니다.");
        }
        if (inquiryType.requiresOrderAndProduct() && (orderId == null || productId == null)) {
            throw new QualityInquiryValidationException("해당 문의 유형에는 주문 ID와 상품 ID가 필요합니다.");
        }
        if (inquiryType.requiresOrderAndDelivery() && (orderId == null || deliveryId == null || deliveryId.isBlank())) {
            throw new QualityInquiryValidationException("배송 문의에는 주문 ID와 배송 ID가 필요합니다.");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public String getDeliveryId() { return deliveryId; }
    public QualityInquiryType getInquiryType() { return inquiryType; }
    public QualityInquiryStatus getStatus() { return status; }
    public String getPriority() { return priority; }
    public String getContent() { return content; }
    public String getSummary() { return summary; }
    public Long getAssignedAdminId() { return assignedAdminId; }
    public String getAdminAnswer() { return adminAnswer; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<QualityInquiryAttachment> getAttachments() { return List.copyOf(attachments); }
}
