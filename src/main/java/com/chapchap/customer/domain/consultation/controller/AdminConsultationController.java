package com.chapchap.customer.domain.consultation.controller;

import com.chapchap.customer.domain.consultation.response.AdminConsultationResponse;
import com.chapchap.customer.domain.consultation.response.ConsultationResponse;
import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/admin/consultations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminConsultationController {
    private final ConsultationService consultationService;

    @GetMapping
    public ResponseEntity<GlobalResponse<List<AdminConsultationResponse>>> findWaitingConsultations() {
        return GlobalResponse.success(consultationService.findWaitingConsultations());
    }

    @PatchMapping("/{consultationId}/assignee")
    public ResponseEntity<GlobalResponse<ConsultationResponse>> acceptConsultation(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long consultationId
    ) {
        return GlobalResponse.success(consultationService.acceptConsultation(requireUserId(principal), consultationId));
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
