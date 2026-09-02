package com.chapchap.customer.domain.consultation.controller;

import com.chapchap.customer.domain.consultation.request.ConsultationRealtimeMessageRequest;
import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import com.chapchap.customer.global.security.websocket.TrustedUserContextHandshakeInterceptor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ConsultationMessageWebSocketController {
    private final ConsultationService consultationService;

    @MessageMapping("/consultations/{consultationId}/messages")
    public void sendMessage(
            @DestinationVariable Long consultationId,
            @Valid ConsultationRealtimeMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        consultationService.saveRealtimeMessage(requirePrincipal(headerAccessor), consultationId, request);
    }

    private GatewayUserPrincipal requirePrincipal(SimpMessageHeaderAccessor headerAccessor) {
        Object principal = headerAccessor.getSessionAttributes() == null
                ? null
                : headerAccessor.getSessionAttributes()
                .get(TrustedUserContextHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        if (principal instanceof GatewayUserPrincipal gatewayUserPrincipal) {
            return gatewayUserPrincipal;
        }
        throw new AccessDeniedException("신뢰할 수 있는 사용자 정보가 없습니다.");
    }
}
