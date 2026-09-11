package com.chapchap.customer.global.config.consultation;

import com.chapchap.customer.domain.consultation.service.websocket.ConsultationWebSocketSecurityInterceptor;
import com.chapchap.customer.domain.consultation.service.websocket.TrustedUserContextHandshakeInterceptor;
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
    private final com.chapchap.customer.domain.consultation.service.websocket.ConsultationConnectionRegistry connections;

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
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(handler -> new org.springframework.web.socket.handler.WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) throws Exception {
                connections.add(session);
                super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                    org.springframework.web.socket.CloseStatus status) throws Exception {
                connections.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(connections);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(consultationWebSocketSecurityInterceptor);
    }
}
