package com.chapchap.customer.global.security.websocket;

import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.global.security.context.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 이미 구독한 연결도 현재 계정/상담 권한을 잃으면 메시지를 수신하지 못한다. */
@Component
@RequiredArgsConstructor
public class ConsultationConnectionRegistry implements ChannelInterceptor {
    private final CurrentAccountVerifier verifier;
    private final ConsultationService consultations;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void add(WebSocketSession session) { sessions.put(session.getId(), session); }
    public void remove(String sessionId) { sessions.remove(sessionId); }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = SimpMessageHeaderAccessor.wrap(message);
        if (accessor.getMessageType() != SimpMessageType.MESSAGE) return message;
        var sessionId = accessor.getSessionId();
        var session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) return null;
        try {
            var principal = verify(session);
            String destination = accessor.getDestination();
            if (destination == null || !destination.matches("/topic/consultations/[1-9][0-9]*"))
                throw new AccessDeniedException("허용되지 않은 상담입니다.");
            consultations.assertWebSocketParticipant(Long.parseLong(destination.substring(destination.lastIndexOf('/') + 1)), principal);
            return message;
        } catch (RuntimeException exception) {
            close(session);
            return null;
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void expireConnections() {
        sessions.values().forEach(session -> {
            try { verify(session); }
            catch (RuntimeException exception) { close(session); }
        });
    }

    private GatewayUserPrincipal verify(WebSocketSession session) {
        Object expiry = session.getAttributes().get(TrustedUserContextHandshakeInterceptor.EXPIRY_ATTRIBUTE);
        Object identity = session.getAttributes().get(TrustedUserContextHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        if (!(expiry instanceof Long expiresAt) || expiresAt <= Instant.now().getEpochSecond()
                || !(identity instanceof GatewayUserPrincipal principal))
            throw new AccessDeniedException("상담 인증이 만료되었습니다.");
        verifier.verify(principal);
        return principal;
    }

    private void close(WebSocketSession session) {
        sessions.remove(session.getId());
        try { session.close(CloseStatus.POLICY_VIOLATION); }
        catch (java.io.IOException ignored) { }
    }
}
