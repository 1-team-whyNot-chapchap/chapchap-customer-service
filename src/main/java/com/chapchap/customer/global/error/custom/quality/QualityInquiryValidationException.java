package com.chapchap.customer.global.error.custom.quality;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class QualityInquiryValidationException extends BusinessException {
    public QualityInquiryValidationException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER_ERROR, message);
    }
}
