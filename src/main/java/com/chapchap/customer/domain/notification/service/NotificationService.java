package com.chapchap.customer.domain.notification.service;

import com.chapchap.customer.domain.notification.entity.Notification;
import com.chapchap.customer.domain.notification.entity.NotificationRead;
import com.chapchap.customer.domain.notification.entity.NotificationRecipientType;
import com.chapchap.customer.domain.notification.repository.NotificationReadRepository;
import com.chapchap.customer.domain.notification.repository.NotificationRepository;
import com.chapchap.customer.domain.notification.response.NotificationResponse;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> findNotifications(GatewayUserPrincipal principal) {
        Long userId = requireUserId(principal);
        return findVisibleNotifications(principal.role(), userId)
                .stream()
                .map(notification -> NotificationResponse.from(notification,
                        notificationReadRepository.existsByNotification_IdAndReaderUserId(notification.getId(), userId)))
                .toList();
    }

    @Transactional
    public void markAsRead(GatewayUserPrincipal principal, Long notificationId) {
        Long userId = requireUserId(principal);
        Notification notification = findVisibleNotification(principal.role(), userId, notificationId);
        if (!notificationReadRepository.existsByNotification_IdAndReaderUserId(notificationId, userId)) {
            notificationReadRepository.save(NotificationRead.create(notification, userId, LocalDateTime.now()));
        }
    }

    @Transactional
    public void markAllAsRead(GatewayUserPrincipal principal) {
        Long userId = requireUserId(principal);
        findVisibleNotifications(principal.role(), userId).forEach(notification -> {
            if (!notificationReadRepository.existsByNotification_IdAndReaderUserId(notification.getId(), userId)) {
                notificationReadRepository.save(NotificationRead.create(notification, userId, LocalDateTime.now()));
            }
        });
    }

    private List<Notification> findVisibleNotifications(RolePolicy role, Long userId) {
        return switch (role) {
            case CUSTOMER -> notificationRepository.findByRecipientTypeAndRecipientUserIdOrderByOccurredAtDescIdDesc(
                    NotificationRecipientType.CUSTOMER, userId
            );
            case RIDER -> notificationRepository.findByRecipientTypeAndRecipientUserIdOrderByOccurredAtDescIdDesc(
                    NotificationRecipientType.RIDER, userId
            );
            case ADMIN -> notificationRepository.findByRecipientTypeAndRecipientUserIdIsNullOrderByOccurredAtDescIdDesc(
                    NotificationRecipientType.ADMIN
            );
            case SUPER_ADMIN -> throw new AccessDeniedException("관리자 역할 그룹 알림 수신 계약에 포함되지 않습니다.");
        };
    }

    private Notification findVisibleNotification(RolePolicy role, Long userId, Long notificationId) {
        return (switch (role) {
            case CUSTOMER -> notificationRepository.findByIdAndRecipientTypeAndRecipientUserId(
                    notificationId, NotificationRecipientType.CUSTOMER, userId
            );
            case RIDER -> notificationRepository.findByIdAndRecipientTypeAndRecipientUserId(
                    notificationId, NotificationRecipientType.RIDER, userId
            );
            case ADMIN -> notificationRepository.findByIdAndRecipientTypeAndRecipientUserIdIsNull(
                    notificationId, NotificationRecipientType.ADMIN
            );
            case SUPER_ADMIN -> throw new AccessDeniedException("관리자 역할 그룹 알림 수신 계약에 포함되지 않습니다.");
        }).orElseThrow(() -> new BusinessException(
                CustomResponseCode.NOT_FOUND_RESOURCE_ERROR,
                "알림을 찾을 수 없습니다."
        ));
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
