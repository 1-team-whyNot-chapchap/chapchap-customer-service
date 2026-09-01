package com.chapchap.customer.global.security.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class IdentityKeyGenerator {

    private static final String HMAC_ALGORITHM  = "HmacSHA256";

    // HMAC 연산에 사용할 서버 전용 비밀키
    // DI만 알고 있어도 동일한 identityKey를 생성할 수 없도록 Secret을 함께 사용한다.
    private final SecretKeySpec secretKey;

    public IdentityKeyGenerator(
            @Value("${IDENTITY_HMAC_SECRET}") String secret
    ) {
        // Secret이 없는 상태로 서버가 실행되는 것을 방지한다.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Identity HMAC 비밀키가 설정되지 않았습니다.");
        }

        // 문자열 형태의 Secret을 HmacSHA256에서 사용할 수 있는 Key 객체로 변환한다.
        this.secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    // 본인인증 결과에서 받은 DI 원문을 서버전용 secret과 HMAC-SHA-256으로 조합하여 identityKey를 생성한다.
    // DI 원문은 DB에 저장하지 않고 반환된 64자리 identityKey만 동일인 판별에 사용한다.
    public String generate(String di) {

        // 잘못된 DI가 암호화 로직까지 들어오는 것을 차단한다.
        if (di == null || di.isBlank()) {
            throw new IllegalStateException("DI 값은 비어 있을 수 없습니다.");
        }
        try {
            // HmacSHA256 연산 객체 생성
            // HmacSHA256: 원본 데이터와 서버 비밀키를 함께 사용해 위변조가 어려운 고정 길이 해시값을 만드는 알고리즘
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            
            // HMAC 연산에 사용할 서버 비밀키 설정
            mac.init(secretKey);
            
            // DI를 UTF-8 byte 배열로 변환한 뒤 HMAC-SHA-256 계산
            // 결과는 32byte의 바이너리 데이터다
            byte[] result = mac.doFinal(
                    di.getBytes(StandardCharsets.UTF_8)
            );

            // 32byte 결과를 DB에 저장하기 쉬운 16진수 문자열로 변환한다.
            // 1byte = 16진수 2자리이므로 최종 identityKey는 64자리다.
            return HexFormat.of().formatHex(result);
        } catch (GeneralSecurityException e) {
            // 알고리즘 또는 Key 초기화 문제 등 암호화 처리 자체가 실패한 경우
            throw new IllegalStateException("identityKey 생성에 실패했습니다.", e);
        }
    }
}
