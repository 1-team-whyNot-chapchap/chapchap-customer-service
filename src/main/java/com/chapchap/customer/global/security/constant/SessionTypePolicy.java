package com.chapchap.customer.global.security.constant;

import lombok.Getter;


// 로그인한 서비스 영역을 구분하는 세션 종류
// 일반 사용자와 관리자 사이트의 토큰 정책을 분리할 때 사용

@Getter
public enum SessionTypePolicy {
    USER("USER"), // 고객·점주·라이더가 일반 서비스에 로그인한 세션
    ADMIN("ADMIN"); // 관리자·최고 관리자가 관리자 사이트에 로그인한 세션

    private final String sessionType;

    SessionTypePolicy(String sessionType) {
        this.sessionType = sessionType;
    }
}
