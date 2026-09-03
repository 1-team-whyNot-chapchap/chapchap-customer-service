package com.chapchap.customer.global.error.custom.knowledge;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class KnowledgeVersionDuplicateException extends BusinessException {
    public KnowledgeVersionDuplicateException() {
        super(CustomResponseCode.DUPLICATED_RESOURCE_ERROR, "같은 Knowledge Version이 이미 등록되어 있습니다.");
    }
}
