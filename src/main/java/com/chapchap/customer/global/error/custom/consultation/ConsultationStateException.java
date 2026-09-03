package com.chapchap.customer.global.error.custom.consultation;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class ConsultationStateException extends BusinessException {
    public ConsultationStateException(String message) {
        super(CustomResponseCode.INVALID_STATE_ERROR, message);
    }
}
