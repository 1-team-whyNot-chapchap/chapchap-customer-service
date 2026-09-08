package com.chapchap.customer.global.config;

import com.chapchap.customer.global.security.websocket.ConsultationWebSocketSecurityInterceptor;
import com.chapchap.customer.global.security.websocket.TrustedUserContextHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ConsultationWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    @Value("${CUSTOMER_WEBSOCKET_ALLOWED_ORIGINS:http://localhost:5173}")
    private String[] allowedOrigins;
    private final TrustedUserContextHandshakeInterceptor trustedUserContextHandshakeInterceptor;
    private final ConsultationWebSocketSecurityInterceptor consultationWebSocketSecurityInterceptor;

    @Override
    public void configureMessageBroker(org.springframework.messaging.simp.config.MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/customer/consultations")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(trustedUserContextHandshakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(consultationWebSocketSecurityInterceptor);
    }
}
