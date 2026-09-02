package com.chapchap.customer.global.security.websocket;

import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ConsultationWebSocketSecurityInterceptor implements ChannelInterceptor {
    private static final Pattern SUBSCRIBE_DESTINATION = Pattern.compile("^/topic/consultations/(\\d+)$");
    private static final Pattern SEND_DESTINATION = Pattern.compile("^/app/consultations/(\\d+)/messages$");

    private final ConsultationService consultationService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command != StompCommand.SUBSCRIBE && command != StompCommand.SEND) {
            return message;
        }

        Pattern destinationPattern = command == StompCommand.SUBSCRIBE
                ? SUBSCRIBE_DESTINATION
                : SEND_DESTINATION;
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("상담 목적지가 없습니다.");
        }
        Matcher matcher = destinationPattern.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        consultationService.assertWebSocketParticipant(
                Long.parseLong(matcher.group(1)),
                requirePrincipal(accessor)
        );
        return message;
    }

    private GatewayUserPrincipal requirePrincipal(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object principal = sessionAttributes == null
                ? null
                : sessionAttributes.get(TrustedUserContextHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        if (principal instanceof GatewayUserPrincipal gatewayUserPrincipal) {
            return gatewayUserPrincipal;
        }
        throw new AccessDeniedException("신뢰할 수 있는 사용자 정보가 없습니다.");
    }
}
