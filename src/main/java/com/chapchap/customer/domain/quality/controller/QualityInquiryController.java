package com.chapchap.customer.domain.quality.controller;

import com.chapchap.customer.domain.quality.request.QualityInquiryCreateRequest;
import com.chapchap.customer.domain.quality.response.QualityInquiryResponse;
import com.chapchap.customer.domain.quality.response.QualityInquirySummaryResponse;
import com.chapchap.customer.domain.quality.service.QualityInquiryService;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/quality-inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER', 'RIDER')")
public class QualityInquiryController {
    private final QualityInquiryService qualityInquiryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GlobalResponse<QualityInquiryResponse>> create(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @Valid @ModelAttribute QualityInquiryCreateRequest request
    ) {
        return GlobalResponse.success(qualityInquiryService.create(requireUserId(principal), request));
    }

    @GetMapping
    public ResponseEntity<GlobalResponse<List<QualityInquirySummaryResponse>>> findMyInquiries(
            @AuthenticationPrincipal GatewayUserPrincipal principal
    ) {
        return GlobalResponse.success(qualityInquiryService.findMyInquiries(requireUserId(principal)));
    }

    @GetMapping("/{qualityInquiryId}")
    public ResponseEntity<GlobalResponse<QualityInquiryResponse>> findMyInquiry(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long qualityInquiryId
    ) {
        return GlobalResponse.success(qualityInquiryService.findMyInquiry(requireUserId(principal), qualityInquiryId));
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
