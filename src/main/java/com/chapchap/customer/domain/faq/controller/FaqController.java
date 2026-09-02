package com.chapchap.customer.domain.faq.controller;

import com.chapchap.customer.domain.faq.request.FaqCreateRequest;
import com.chapchap.customer.domain.faq.request.FaqUpdateRequest;
import com.chapchap.customer.domain.faq.response.FaqResponse;
import com.chapchap.customer.domain.faq.service.FaqService;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class FaqController {
    private final FaqService faqService;

    @GetMapping("/faqs")
    public ResponseEntity<GlobalResponse<List<FaqResponse>>> findPublicFaqs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword
    ) {
        return GlobalResponse.success(faqService.findPublicFaqs(category, keyword));
    }

    @GetMapping("/faqs/{faqId}")
    public ResponseEntity<GlobalResponse<FaqResponse>> findPublicFaq(@PathVariable Long faqId) {
        return GlobalResponse.success(faqService.findPublicFaq(faqId));
    }

    @PostMapping("/admin/faqs")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GlobalResponse<FaqResponse>> createFaq(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @Valid @RequestBody FaqCreateRequest request
    ) {
        return GlobalResponse.success(faqService.createFaq(requireUserId(principal), request));
    }

    @PutMapping("/admin/faqs/{faqId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GlobalResponse<FaqResponse>> updateFaq(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long faqId,
            @Valid @RequestBody FaqUpdateRequest request
    ) {
        return GlobalResponse.success(faqService.updateFaq(requireUserId(principal), faqId, request));
    }

    @PatchMapping("/admin/faqs/{faqId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GlobalResponse<FaqResponse>> deactivateFaq(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long faqId
    ) {
        return GlobalResponse.success(faqService.deactivateFaq(requireUserId(principal), faqId));
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
