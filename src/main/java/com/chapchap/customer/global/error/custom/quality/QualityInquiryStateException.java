package com.chapchap.customer.global.error.custom.quality;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class QualityInquiryStateException extends BusinessException {
    public QualityInquiryStateException(String message) {
        super(CustomResponseCode.INVALID_STATE_ERROR, message);
    }
}
