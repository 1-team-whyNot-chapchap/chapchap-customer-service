package com.chapchap.customer.global.error.custom.quality;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class QualityInquiryNotFoundException extends BusinessException {
    public QualityInquiryNotFoundException() {
        super(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR, "품질 문의를 찾을 수 없습니다.");
    }
}
