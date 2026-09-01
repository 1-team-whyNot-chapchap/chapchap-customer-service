package com.chapchap.customer.global.security.constant;

import lombok.Getter;

@Getter
public enum ConsentStatusPolicy {
    
    AGREED("AGREED"),       // 정책 동의
    DECLINED("DECLINED"),   // 정책 거절
    WITHDRAWN("WITHDRAWN"); // 기존 동의 철회

    private final String status;

    ConsentStatusPolicy(String status) {
        this.status = status;
    }

}
