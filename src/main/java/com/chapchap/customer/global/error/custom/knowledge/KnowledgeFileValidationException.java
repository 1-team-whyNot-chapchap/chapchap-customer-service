package com.chapchap.customer.global.error.custom.knowledge;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class KnowledgeFileValidationException extends BusinessException {
    public KnowledgeFileValidationException() {
        super(CustomResponseCode.INVALID_PARAMETER_ERROR, "허용되지 않았거나 손상된 Knowledge 파일입니다.");
    }
}
