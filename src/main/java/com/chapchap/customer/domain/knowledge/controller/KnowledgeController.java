package com.chapchap.customer.domain.knowledge.controller;

import com.chapchap.customer.domain.knowledge.request.KnowledgeVersionRegisterRequest;
import com.chapchap.customer.domain.knowledge.response.KnowledgeVersionResponse;
import com.chapchap.customer.domain.knowledge.service.KnowledgeRegistrationService;
import com.chapchap.customer.domain.knowledge.service.KnowledgeVersionQueryService;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {
    private final KnowledgeRegistrationService knowledgeRegistrationService;
    private final KnowledgeVersionQueryService knowledgeVersionQueryService;

    @PostMapping(value = "/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GlobalResponse<KnowledgeVersionResponse>> registerVersion(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @Valid @ModelAttribute KnowledgeVersionRegisterRequest request
    ) {
        return GlobalResponse.success(knowledgeRegistrationService.register(requireUserId(principal), request));
    }

    @GetMapping("/versions/{knowledgeVersionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GlobalResponse<KnowledgeVersionResponse>> getVersion(
            @PathVariable Long knowledgeVersionId
    ) {
        return GlobalResponse.success(knowledgeVersionQueryService.get(knowledgeVersionId));
    }

    private Long requireUserId(GatewayUserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(CustomResponseCode.UNAUTHENTICATED_ERROR, "인증 정보가 없습니다.");
        }

        try {
            return Long.parseLong(principal.userId());
        } catch (NumberFormatException exception) {
            throw new BusinessException(CustomResponseCode.UNAUTHENTICATED_ERROR, "유효하지 않은 사용자 ID입니다.");
        }
    }
}
