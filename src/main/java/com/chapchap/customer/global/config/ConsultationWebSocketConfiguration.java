package com.chapchap.customer.global.config;

import com.chapchap.customer.global.security.websocket.ConsultationWebSocketSecurityInterceptor;
import com.chapchap.customer.global.security.websocket.TrustedUserContextHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ConsultationWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
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
                .addInterceptors(trustedUserContextHandshakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(consultationWebSocketSecurityInterceptor);
    }
}
