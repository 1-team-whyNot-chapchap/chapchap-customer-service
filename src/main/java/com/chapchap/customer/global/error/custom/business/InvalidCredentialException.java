package com.chapchap.customer.global.error.custom.business;

import com.chapchap.auth.global.error.custom.BusinessException;
import com.chapchap.auth.global.response.constant.CustomResponseCode;

public class InvalidCredentialException extends BusinessException {
    public InvalidCredentialException() {
        super(CustomResponseCode.UNAUTHENTICATED_ERROR, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
