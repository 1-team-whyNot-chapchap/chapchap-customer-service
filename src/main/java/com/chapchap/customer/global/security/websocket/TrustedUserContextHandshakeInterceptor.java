package com.chapchap.customer.global.security.websocket;

import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class TrustedUserContextHandshakeInterceptor implements HandshakeInterceptor {
    public static final String PRINCIPAL_ATTRIBUTE = "gatewayUserPrincipal";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof GatewayUserPrincipal principal)) {
            return false;
        }
        attributes.put(PRINCIPAL_ATTRIBUTE, principal);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Exception exception
    ) {
        // STOMP 세션 속성은 beforeHandshake에서만 설정한다.
    }
}
