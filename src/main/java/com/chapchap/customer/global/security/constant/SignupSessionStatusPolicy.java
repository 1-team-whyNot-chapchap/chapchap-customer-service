package com.chapchap.customer.global.security.constant;

import lombok.Getter;

@Getter
public enum SignupSessionStatusPolicy {

    // 회원가입 세션은 총 5단계 이다.
    PENDING("PENDING"),  // 소셜 로그인은 완료됐지만 본인인증 전인 가입 대기 상태
    IDENTITY_VERIFIED("IDENTITY_VERIFIED"), // 본인인증이 정상 완료된 상태
    COMPLETED("COMPLETED"), // 약관 동의 및 회원가입까지 모두 완료 된 상태
    FAILED("FAILED"), // 가입 처리 중 오류나 검증 실패로 진행이 중단된 상태
    EXPIRED("EXPIRED"); // 가입 세션 유효시간 15분이 지나 만료된 상태

    private final String status;

    SignupSessionStatusPolicy(String status) {
        this.status = status;
    }
}
