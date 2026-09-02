package com.chapchap.customer.domain.notification.controller;

import com.chapchap.customer.domain.notification.response.NotificationResponse;
import com.chapchap.customer.domain.notification.service.CustomerNotificationSseService;
import com.chapchap.customer.domain.notification.service.NotificationService;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/customer/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER', 'RIDER', 'ADMIN')")
public class NotificationController {
    private final NotificationService notificationService;
    private final CustomerNotificationSseService customerNotificationSseService;

    @GetMapping
    public ResponseEntity<GlobalResponse<List<NotificationResponse>>> findNotifications(
            @AuthenticationPrincipal GatewayUserPrincipal principal
    ) {
        return GlobalResponse.success(notificationService.findNotifications(principal));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<GlobalResponse<Void>> markAsRead(
            @AuthenticationPrincipal GatewayUserPrincipal principal,
            @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(principal, notificationId);
        return GlobalResponse.success(null);
    }

    @PostMapping("/read-all")
    public ResponseEntity<GlobalResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal GatewayUserPrincipal principal
    ) {
        notificationService.markAllAsRead(principal);
        return GlobalResponse.success(null);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public SseEmitter connectCustomerStream(@AuthenticationPrincipal GatewayUserPrincipal principal) {
        return customerNotificationSseService.connect(requireUserId(principal));
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
