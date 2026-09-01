package com.chapchap.customer.global.security.filter;

import com.chapchap.auth.global.response.GlobalResponse;
import com.chapchap.auth.global.response.constant.CustomResponseCode;
import com.chapchap.auth.global.security.config.AllowedOriginProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * HttpOnly Refresh Cookie를 사용하는 상태 변경 API의 교차 Origin 요청을 거부한다.
 * Origin 헤더가 없는 비브라우저/동일 Origin 호출은 Cookie SameSite 정책에 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class RefreshCookieOriginFilter extends OncePerRequestFilter {
    private static final Set<String> COOKIE_MUTATING_PATHS = Set.of(
            "/api/auth/reissue-token",
            "/api/auth/logout"
    );

    private final AllowedOriginProperties allowedOriginProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !COOKIE_MUTATING_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOriginProperties.contains(origin)) {
            response.setStatus(CustomResponseCode.UNAUTHORIZED_ERROR.getHttpStatus().value());
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(), GlobalResponse.from(CustomResponseCode.UNAUTHORIZED_ERROR));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
