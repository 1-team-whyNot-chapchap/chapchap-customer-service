package com.chapchap.customer.domain.quality.request;

import com.chapchap.customer.domain.quality.constant.QualityInquiryStatus;
import com.chapchap.customer.domain.quality.constant.QualityInquiryType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QualityInquiryProcessRequest(
        @NotNull QualityInquiryType inquiryType,
        @Size(max = 20) String priority,
        @NotNull QualityInquiryStatus status,
        String adminAnswer
) {
}
