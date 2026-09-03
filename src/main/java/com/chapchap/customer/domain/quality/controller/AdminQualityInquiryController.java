package com.chapchap.customer.domain.quality.controller;

import com.chapchap.customer.domain.quality.request.QualityInquiryProcessRequest;
import com.chapchap.customer.domain.quality.response.QualityInquiryResponse;
import com.chapchap.customer.domain.quality.response.QualityInquirySummaryResponse;
import com.chapchap.customer.domain.quality.service.QualityInquiryService;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/admin/quality-inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminQualityInquiryController {
    private final QualityInquiryService qualityInquiryService;

    @GetMapping
    public ResponseEntity<GlobalResponse<List<QualityInquirySummaryResponse>>> findAll() {
        return GlobalResponse.success(qualityInquiryService.findAllInquiries());
    }

    @GetMapping("/{qualityInquiryId}")
    public ResponseEntity<GlobalResponse<QualityInquiryResponse>> findOne(@PathVariable Long qualityInquiryId) {
        return GlobalResponse.success(qualityInquiryService.findInquiry(qualityInquiryId));
    }

    @PatchMapping("/{qualityInquiryId}")
    public ResponseEntity<GlobalResponse<QualityInquiryResponse>> process(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long qualityInquiryId,
            @Valid @RequestBody QualityInquiryProcessRequest request
    ) {
        return GlobalResponse.success(qualityInquiryService.process(requireUserId(principal), qualityInquiryId, request));
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
