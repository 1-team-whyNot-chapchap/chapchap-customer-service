package com.chapchap.customer.global.security.policy;

import com.chapchap.auth.global.security.constant.SessionTypePolicy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuthSessionPolicy {

    // 세션 종류에 따라 최초 생성되는 유휴 만료 시각 계산
    public LocalDateTime calculateIdleExpiresAt(
            SessionTypePolicy sessionType,
            LocalDateTime now
    ) {
        if (sessionType == SessionTypePolicy.ADMIN) {
            return now.plusMinutes(30);
        }

        return now.plusDays(14);
    }

    // 세션 종류에 따라 최초 로그인 기준 절대 만료 시각 계산
    public LocalDateTime calculateAbsoluteExpiresAt(
            SessionTypePolicy sessionType,
            LocalDateTime now
    ) {
        if (sessionType == SessionTypePolicy.ADMIN) {
            return now.plusHours(8);
        }

        return now.plusDays(30);
    }

    // 일반 사용자 Refresh Token 재발급 성공 시 새로운 유휴 만료 시작 계산
    // 유휴 만료 시작은 최초 로그인 기준 절대 만료 시작을 넘을 수 없다.
    public LocalDateTime calculateExtendedUserIdleExpiresAt(
        LocalDateTime now,
        LocalDateTime absoluteExpiresAt
    ) {
        LocalDateTime extendedIdleExpiresAt = now.plusDays(14);

        if (extendedIdleExpiresAt.isAfter(absoluteExpiresAt)) {
            return absoluteExpiresAt;
        }

        return extendedIdleExpiresAt;
    }
}