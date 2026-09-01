package com.chapchap.customer.global.security.constant;

import lombok.Getter;

@Getter
public enum UserStatusPolicy {
    // 정상 이용 가능
    ACTIVE("ACTIVE"),
    // 이용 정지
    SUSPENDED("SUSPENDED"),
    // 탈퇴 완료
    WITHDRAWN("WITHDRAWN");

    private final String status;

    UserStatusPolicy(String status) {
        this.status = status;
    }
}
