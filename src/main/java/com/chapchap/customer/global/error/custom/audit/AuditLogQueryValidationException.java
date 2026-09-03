package com.chapchap.customer.global.error.custom.audit;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class AuditLogQueryValidationException extends BusinessException {
    public AuditLogQueryValidationException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER_ERROR, message);
    }
}
