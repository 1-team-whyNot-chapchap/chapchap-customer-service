package com.chapchap.customer.global.error.custom.knowledge;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class KnowledgeVersionNotFoundException extends BusinessException {
    public KnowledgeVersionNotFoundException() {
        super(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR, "Knowledge Version을 찾을 수 없습니다.");
    }
}
