package com.chapchap.customer.global.security.filter;

import com.chapchap.auth.global.security.oauth2.DelegatingOAuth2UserService;
import com.chapchap.auth.global.security.oauth2.OAuth2FailerHandler;
import com.chapchap.auth.global.security.oauth2.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 메소드 레벨 권한 제어 활성화
@RequiredArgsConstructor
public class SecurityConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity, HeaderAuthenticationFilter headerAuthenticationFilter, AdminPasswordChangeRequiredFilter adminPasswordChangeRequiredFilter, RefreshCookieOriginFilter refreshCookieOriginFilter, TraceIdFilter traceIdFilter, DelegatingOAuth2UserService delegatingOAuth2UserService, OAuth2SuccessHandler oAuth2SuccessHandler, OAuth2FailerHandler oAuth2FailerHandler) throws Exception {
        return httpSecurity
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 비활성화
                .httpBasic(AbstractHttpConfigurer::disable) // 화면 생성 비활성화
                .formLogin(AbstractHttpConfigurer::disable) // 폼로그인 기능 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(traceIdFilter, HeaderAuthenticationFilter.class)
                .addFilterAfter(refreshCookieOriginFilter, TraceIdFilter.class)
                .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)// CSRF 토큰 인증 비활성화
                .addFilterAfter(adminPasswordChangeRequiredFilter, HeaderAuthenticationFilter.class)
                .authorizeHttpRequests(request -> request
                        .requestMatchers(
                                "/api/auth/oauth2/**",
                                "/api/auth/signup/complete",
                                "/api/auth/reissue-token",
                                "/api/auth/admin/login",
                                "/api/auth/admin/password/initial",
                                "/api/auth/policies/current",
                                "/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                       .authorizationEndpoint(endPoint -> endPoint.baseUri("/api/auth/oauth2/authorization")) // 기본 경로 설정
                       .redirectionEndpoint(endPoint -> endPoint.baseUri("/api/auth/oauth2/callback/*")) // 리다이렉트 경로 설정
                       .userInfoEndpoint(userInfo ->
                            userInfo.userService(delegatingOAuth2UserService) // provider 라우팅 처리 서비스 등록
                       )
                       .successHandler(oAuth2SuccessHandler) // 성공 핸들러 등록
                       .failureHandler(oAuth2FailerHandler) // 실패 핸들러 등록
                )
                .build();
    }
}
