package com.chapchap.customer.domain.audit.controller;

import com.chapchap.customer.domain.audit.request.AuditLogSearchRequest;
import com.chapchap.customer.domain.audit.response.AuditLogPageResponse;
import com.chapchap.customer.domain.audit.service.AuditLogQueryService;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AuditLogController {
    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    public ResponseEntity<GlobalResponse<AuditLogPageResponse>> search(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @Valid @ModelAttribute AuditLogSearchRequest request
    ) {
        if (principal == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        return GlobalResponse.success(auditLogQueryService.search(request, principal.role()));
    }
}
