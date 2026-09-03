package com.chapchap.customer.global.error.custom.faq;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class FaqNotFoundException extends BusinessException {
    public FaqNotFoundException() {
        super(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR, "FAQ를 찾을 수 없습니다.");
    }
}
