package com.chapchap.customer.global.config.openapi;


import com.chapchap.customer.global.response.constant.CustomResponseCode;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "200", description = "SUCCESS")
public @interface CustomApiResponse {
    CustomResponseCode[] value();
}
