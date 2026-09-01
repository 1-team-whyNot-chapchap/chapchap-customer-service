package com.chapchap.customer.global.security.constant;

import lombok.Getter;

@Getter
public enum PolicyTypePolicy {

    TERMS_OF_SERVICE("TERMS_OF_SERVICE"), // 서비스 이용약관 - 필수
    PRIVACY_POLICY("PRIVACY_POLICY"),     // 개인정보 처리 관련 정책 - 필수
    MARKETING_EMAIL("MARKETING_EMAIL");   // 이메일 광고/마케팅 수신 - 선택

    private final String type;

    PolicyTypePolicy(String type) {
        this.type = type;
    }
}
