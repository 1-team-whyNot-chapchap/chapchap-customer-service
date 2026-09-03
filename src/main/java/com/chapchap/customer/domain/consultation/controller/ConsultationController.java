package com.chapchap.customer.domain.consultation.controller;

import com.chapchap.customer.domain.consultation.request.ConsultationCreateRequest;
import com.chapchap.customer.domain.consultation.response.ConsultationCreatedResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationMessagesResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationResponse;
import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/consultations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER', 'RIDER')")
public class ConsultationController {
    private final ConsultationService consultationService;

    @PostMapping
    public ResponseEntity<GlobalResponse<ConsultationCreatedResponse>> createConsultation(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @Valid @RequestBody ConsultationCreateRequest request
    ) {
        return GlobalResponse.success(consultationService.createConsultation(requireUserId(principal), request));
    }

    @PostMapping("/{consultationId}/admin-handoffs")
    public ResponseEntity<GlobalResponse<ConsultationResponse>> requestAdminHandoff(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long consultationId
    ) {
        return GlobalResponse.success(consultationService.requestAdminHandoff(requireUserId(principal), consultationId));
    }

    @GetMapping("/{consultationId}")
    public ResponseEntity<GlobalResponse<ConsultationResponse>> findMyConsultation(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long consultationId
    ) {
        return GlobalResponse.success(consultationService.findMyConsultation(requireUserId(principal), consultationId));
    }

    @GetMapping("/{consultationId}/messages")
    public ResponseEntity<GlobalResponse<ConsultationMessagesResponse>> findMyConsultationMessages(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long consultationId
    ) {
        return GlobalResponse.success(
                consultationService.findMyConsultationMessages(requireUserId(principal), consultationId)
        );
    }

    private Long requireUserId(GatewayUserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }

        try {
            return Long.parseLong(principal.userId());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("유효하지 않은 사용자 ID입니다.");
        }
    }
}
