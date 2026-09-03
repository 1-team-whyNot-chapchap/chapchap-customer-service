package com.chapchap.customer.global.error.custom.knowledge;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class KnowledgeVersionStateException extends BusinessException {
    public KnowledgeVersionStateException(String message) {
        super(CustomResponseCode.INVALID_STATE_ERROR, message);
    }
}
