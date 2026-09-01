package com.chapchap.customer.global.error.custom;

import com.chapchap.auth.global.response.constant.CustomResponseCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final CustomResponseCode customResponseCode;

    public BusinessException(CustomResponseCode customResponseCode,String message) {
        super(message);
        this.customResponseCode = customResponseCode;
    }
}
