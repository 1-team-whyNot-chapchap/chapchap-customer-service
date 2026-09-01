package com.chapchap.customer.global.error.custom.business;

import com.chapchap.auth.global.error.custom.BusinessException;
import com.chapchap.auth.global.response.constant.CustomResponseCode;

public class DuplicatedResourceException extends BusinessException {
    public DuplicatedResourceException(String message) {
        super(CustomResponseCode.DUPLICATED_RESOURCE_ERROR, message);
    }
}
