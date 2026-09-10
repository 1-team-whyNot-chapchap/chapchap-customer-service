package com.chapchap.customer.domain.quality.constant;

public enum QualityInquiryType {
    DAMAGED,
    MISSING,
    QUALITY,
    DELIVERY,
    OTHER;

    public boolean requiresOrderAndProduct() {
        return this == DAMAGED || this == MISSING || this == QUALITY;
    }

    public boolean requiresOrderAndDelivery() {
        return this == DELIVERY;
    }
}
