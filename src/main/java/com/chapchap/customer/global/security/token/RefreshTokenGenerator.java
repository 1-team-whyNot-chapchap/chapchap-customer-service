package com.chapchap.customer.global.security.token;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshTokenGenerator {
    
    // 예측하기 어려운 리프레시 토큰 생성을 위한 보안 난수 생성기
    private final SecureRandom secureRandom = new SecureRandom();
    
    // 로그인 또는 재발급 성공 시 새로운 리프레시 토큰 원문 생성
    public String generate() {
        
        // 32Byte = 256bit 크기의 난수 생성
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        
        // URL 이나 쿠키에서 안전하게 사용할 수 있도록 Base64 URL 형식으로 변환
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}
