package com.chapchap.customer.global.security.websocket;

import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConsultationWebSocketSecurityInterceptorTest {
    private final ConsultationService consultationService = mock(ConsultationService.class);
    private final ConsultationWebSocketSecurityInterceptor interceptor =
            new ConsultationWebSocketSecurityInterceptor(consultationService);

    @Test
    void validatesConsultationParticipantBeforeSubscription() {
        GatewayUserPrincipal principal = new GatewayUserPrincipal("7", RolePolicy.CUSTOMER);
        Message<?> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/consultations/3",
                Map.of(TrustedUserContextHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal)
        );

        interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));

        verify(consultationService).assertWebSocketParticipant(3L, principal);
    }

    @Test
    void rejectsSubscriptionWithoutHandshakePrincipal() {
        Message<?> message = stompMessage(StompCommand.SUBSCRIBE, "/topic/consultations/3", Map.of());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<?> stompMessage(StompCommand command, String destination, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
