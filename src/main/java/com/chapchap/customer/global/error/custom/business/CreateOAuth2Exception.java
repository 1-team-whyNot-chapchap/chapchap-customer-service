package com.chapchap.customer.global.error.custom.business;

import com.chapchap.auth.global.error.custom.BusinessException;
import com.chapchap.auth.global.response.constant.CustomResponseCode;

public class CreateOAuth2Exception extends BusinessException {
    public CreateOAuth2Exception(String message) {
        super(CustomResponseCode.OAUTH2_ERROR,message);
    }
}
