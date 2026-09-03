package com.chapchap.customer.domain.quality.entity;

public enum QualityInquiryStatus {
    RECEIVED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public boolean canTransitionTo(QualityInquiryStatus nextStatus) {
        return (this == RECEIVED && nextStatus == IN_PROGRESS)
                || (this == IN_PROGRESS && nextStatus == RESOLVED)
                || (this == RESOLVED && nextStatus == CLOSED);
    }
}
