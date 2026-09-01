package com.chapchap.customer.global.security.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class RefreshTokenHasher {
    
    // 리프레시 토큰 원문을 SHA-256으로 해시하여 DB 저장/조회용 값으로 변환
    public String hash(String refreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            
            // 문자열 토큰을 UTF-8 바이트로 변환한 뒤 SHA-256 해시 처리
            byte[] hashBytes = messageDigest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );
            
            // SHA-256 결과를 Base64 문자열로 변환
            return Base64.getEncoder()
                       .encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 Java 기본 제공 알고리즘이므로 발생하면 서버 설정 문제로 처리
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
