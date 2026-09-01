package com.chapchap.customer.global.error.custom.business;

import com.chapchap.auth.global.error.custom.BusinessException;
import com.chapchap.auth.global.response.constant.CustomResponseCode;

/** 요청 형식은 유효하지만 현재 업무 상태 때문에 수행할 수 없는 경우다. */
public class InvalidStateException extends BusinessException {
    public InvalidStateException(String message) {
        super(CustomResponseCode.INVALID_STATE_ERROR, message);
    }
}
