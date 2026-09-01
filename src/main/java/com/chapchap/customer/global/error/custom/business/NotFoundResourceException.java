package com.chapchap.customer.global.error.custom.business;

import com.chapchap.auth.global.error.custom.BusinessException;
import com.chapchap.auth.global.response.constant.CustomResponseCode;

public class NotFoundResourceException extends BusinessException {
    public NotFoundResourceException(String message) {
        super(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR, message);
    }
}
