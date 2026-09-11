package com.chapchap.customer.global.security.filter;

import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class GatewayUserContextFilter extends OncePerRequestFilter {
    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";
    private static final Pattern POSITIVE_NUMERIC_USER_ID = Pattern.compile("[1-9][0-9]*");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            resolvePrincipal(request).ifPresent(this::setAuthentication);
        }
        filterChain.doFilter(request, response);
    }

    private Optional<GatewayUserPrincipal> resolvePrincipal(HttpServletRequest request) {
        String userId = getSingleHeader(request, USER_ID_HEADER);
        String roleName = getSingleHeader(request, USER_ROLE_HEADER);

        if (userId == null || userId.isBlank() || !isNumericUserId(userId)
                || roleName == null || roleName.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new GatewayUserPrincipal(userId, RolePolicy.valueOf(roleName)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String getSingleHeader(HttpServletRequest request, String headerName) {
        Enumeration<String> headers = request.getHeaders(headerName);
        if (!headers.hasMoreElements()) {
            return null;
        }

        String headerValue = headers.nextElement();
        return headers.hasMoreElements() ? null : headerValue;
    }

    private boolean isNumericUserId(String userId) {
        if (!POSITIVE_NUMERIC_USER_ID.matcher(userId).matches()) {
            return false;
        }

        try {
            return Long.parseLong(userId) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void setAuthentication(GatewayUserPrincipal principal) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().getRole()))
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}
