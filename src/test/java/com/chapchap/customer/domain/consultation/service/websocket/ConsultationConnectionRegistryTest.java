package com.chapchap.customer.domain.consultation.service.websocket;

import com.chapchap.customer.global.security.context.CurrentAccountVerifier;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;

import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.global.security.context.*;
import com.chapchap.customer.global.security.constant.RolePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.socket.*;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultationConnectionRegistryTest {
    @Test
    void alreadySubscribedAdministratorCannotReceiveAfterSuspension() throws Exception {
        var verifier = mock(CurrentAccountVerifier.class);
        var consultations = mock(ConsultationService.class);
        var registry = new ConsultationConnectionRegistry(verifier, consultations);
        var principal = new GatewayUserPrincipal("1", RolePolicy.ADMIN);
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("socket-1");
        when(session.getAttributes()).thenReturn(Map.of(
                TrustedUserContextHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal,
                TrustedUserContextHandshakeInterceptor.EXPIRY_ATTRIBUTE, Instant.now().plusSeconds(60).getEpochSecond()));
        registry.add(session);
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("socket-1"); headers.setDestination("/topic/consultations/3");
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThat(registry.preSend(message, null)).isSameAs(message);
        verify(consultations).assertWebSocketParticipant(3L, principal);
        doThrow(new BadCredentialsException("suspended")).when(verifier).verify(principal);
        assertThat(registry.preSend(message, null)).isNull();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoMoreInteractions(consultations);
    }

    @Test
    void expiredConnectionIsClosedWithoutDeliveringMessages() throws Exception {
        var verifier = mock(CurrentAccountVerifier.class);
        var registry = new ConsultationConnectionRegistry(verifier, mock(ConsultationService.class));
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("expired");
        when(session.getAttributes()).thenReturn(Map.of(TrustedUserContextHandshakeInterceptor.EXPIRY_ATTRIBUTE, 1L));
        registry.add(session); registry.expireConnections();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(verifier);
    }
}
