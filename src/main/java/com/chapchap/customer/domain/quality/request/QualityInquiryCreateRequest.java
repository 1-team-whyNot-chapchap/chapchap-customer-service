package com.chapchap.customer.domain.quality.request;

import com.chapchap.customer.domain.quality.entity.QualityInquiryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

public class QualityInquiryCreateRequest {
    @NotNull
    private QualityInquiryType inquiryType;

    @NotBlank
    private String content;

    @Positive
    private Long orderId;

    @Positive
    private Long productId;

    @Size(max = 64)
    private String deliveryId;

    private List<MultipartFile> attachments = new ArrayList<>();

    public QualityInquiryType getInquiryType() { return inquiryType; }
    public void setInquiryType(QualityInquiryType inquiryType) { this.inquiryType = inquiryType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
    public List<MultipartFile> getAttachments() { return attachments; }
    public void setAttachments(List<MultipartFile> attachments) { this.attachments = attachments == null ? new ArrayList<>() : attachments; }
}
