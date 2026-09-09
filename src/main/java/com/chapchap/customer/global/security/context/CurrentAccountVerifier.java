package com.chapchap.customer.global.security.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Auth가 소유한 현재 상태만 확인한다. 사용자 DB를 복제하거나 역할을 추측하지 않는다. */
@Service
public class CurrentAccountVerifier {
    private final RestClient client;

    public CurrentAccountVerifier(@Value("${customer.auth.base-url:http://localhost:8081}") String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public void verify(GatewayUserPrincipal principal) {
        if (principal == null) throw new BadCredentialsException("현재 계정 확인이 필요합니다.");
        try {
            var result = client.get().uri("/api/auth/me")
                    .header("X-User-Id", principal.userId())
                    .header("X-User-Role", principal.role().name())
                    .retrieve().body(AccountEnvelope.class);
            if (result == null || !"00".equals(result.code()) || result.data() == null
                    || !principal.userId().equals(result.data().userId())
                    || !principal.role().name().equals(result.data().role())
                    || !"ACTIVE".equals(result.data().status())) {
                throw new BadCredentialsException("계정 상태가 변경되었습니다.");
            }
        } catch (Exception exception) {
            // 외부 응답/credential은 로그나 클라이언트 오류로 노출하지 않는다. 실패 시 접근 거부.
            throw new BadCredentialsException("현재 계정을 확인할 수 없습니다.");
        }
    }

    public record AccountEnvelope(String code, Account data) { }
    public record Account(String userId, String role, String status) { }
}
