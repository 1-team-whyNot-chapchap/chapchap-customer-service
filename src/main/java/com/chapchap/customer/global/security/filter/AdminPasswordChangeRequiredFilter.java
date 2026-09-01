package com.chapchap.customer.global.security.filter;

import com.chapchap.auth.domain.admin.repository.AdminCredentialRepository;
import com.chapchap.auth.global.response.GlobalResponse;
import com.chapchap.auth.global.response.constant.CustomResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminPasswordChangeRequiredFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/admin/password",
            "/api/auth/admin/password/initial",
            "/api/auth/logout"
    );

    private final AdminCredentialRepository adminCredentialRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isAdministrator(authentication) && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            Long userId = parseUserId(authentication.getName());
            if (userId != null && adminCredentialRepository.findByUserId(userId)
                    .map(credential -> credential.isMustChangePassword())
                    .orElse(false)) {
                response.setStatus(CustomResponseCode.UNAUTHORIZED_ERROR.getHttpStatus().value());
                response.setContentType("application/json");
                objectMapper.writeValue(response.getWriter(), GlobalResponse.from(CustomResponseCode.UNAUTHORIZED_ERROR));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAdministrator(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }

    private Long parseUserId(String name) {
        try {
            return Long.valueOf(name);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
