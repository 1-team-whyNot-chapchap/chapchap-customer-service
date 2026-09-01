package com.chapchap.customer.global.error.custom.business;

import com.chapchap.auth.global.error.custom.BusinessException;
import com.chapchap.auth.global.response.constant.CustomResponseCode;

public class NotRegisteredException extends BusinessException {
    public NotRegisteredException(String message) {
        super(CustomResponseCode.NOT_REGISTERED_ERROR, message);
    }
}
