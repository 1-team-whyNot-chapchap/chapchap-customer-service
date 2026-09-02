package com.chapchap.customer.global.security.websocket;

import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TrustedUserContextHandshakeInterceptorTest {
    private final TrustedUserContextHandshakeInterceptor interceptor = new TrustedUserContextHandshakeInterceptor();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void copiesOnlyGatewayAuthenticatedPrincipalToStompSession() {
        GatewayUserPrincipal principal = new GatewayUserPrincipal("7", RolePolicy.CUSTOMER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of())
        );
        Map<String, Object> attributes = new java.util.HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                mock(ServerHttpRequest.class),
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(TrustedUserContextHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal);
    }
}
