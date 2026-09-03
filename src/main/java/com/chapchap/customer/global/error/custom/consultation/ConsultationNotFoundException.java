package com.chapchap.customer.global.error.custom.consultation;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class ConsultationNotFoundException extends BusinessException {
    public ConsultationNotFoundException() {
        super(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR, "상담을 찾을 수 없습니다.");
    }
}
