package com.chapchap.customer.global.security.filter;

import com.chapchap.customer.global.security.context.CurrentAccountVerifier;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CurrentAccountFilter extends OncePerRequestFilter {
    private final CurrentAccountVerifier verifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof GatewayUserPrincipal principal) {
            try {
                verifier.verify(principal);
            } catch (AuthenticationException exception) {
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"E03\",\"message\":\"다시 로그인해 주세요.\",\"data\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
