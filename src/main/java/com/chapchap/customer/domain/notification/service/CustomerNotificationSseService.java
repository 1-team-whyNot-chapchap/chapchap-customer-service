package com.chapchap.customer.domain.notification.service;

import com.chapchap.customer.domain.notification.response.NotificationResponse;
import com.chapchap.customer.global.security.context.CurrentAccountVerifier;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import com.chapchap.customer.global.security.constant.RolePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CustomerNotificationSseService {
    private final CurrentAccountVerifier verifier;
    private final ConcurrentHashMap<GatewayUserPrincipal, Set<Lease>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId, long expiresAt) {
        return connect(new GatewayUserPrincipal(userId.toString(), RolePolicy.CUSTOMER), expiresAt);
    }

    public SseEmitter connect(GatewayUserPrincipal userId, long expiresAt) {
        if (userId == null || !Set.of(RolePolicy.CUSTOMER, RolePolicy.RIDER, RolePolicy.ADMIN).contains(userId.role()))
            throw new AccessDeniedException("알림 수신 권한이 없습니다.");
        verifier.verify(userId);
        long ttl = Math.min(120_000L, expiresAt * 1000L - System.currentTimeMillis());
        if (ttl <= 0) throw new AccessDeniedException("알림 인증이 만료되었습니다.");
        SseEmitter emitter = new SseEmitter(ttl);
        Lease lease = new Lease(emitter, expiresAt);
        emittersByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(lease);
        emitter.onCompletion(() -> remove(userId, lease));
        emitter.onTimeout(() -> { remove(userId, lease); emitter.complete(); });
        emitter.onError(ignored -> remove(userId, lease));
        try { emitter.send(SseEmitter.event().name("ready").data("connected")); }
        catch (IOException exception) { remove(userId, lease); emitter.complete(); }
        return emitter;
    }

    public void publish(Long userId, NotificationResponse notification) {
        publish(RolePolicy.CUSTOMER, userId, notification);
    }

    public void publish(RolePolicy role, Long recipientUserId, NotificationResponse notification) {
        emittersByUserId.keySet().forEach(principal -> {
            if (principal.role() != role) return;
            if (role != RolePolicy.ADMIN && !principal.userId().equals(String.valueOf(recipientUserId))) return;
            if (!verifyUser(principal)) return;
            emittersByUserId.getOrDefault(principal, Set.of()).forEach(lease ->
                    send(principal, lease, SseEmitter.event().name("notification").data(notification)));
        });
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        emittersByUserId.forEach((userId, leases) -> {
            if (verifyUser(userId)) leases.forEach(lease -> send(userId, lease, SseEmitter.event().comment("heartbeat")));
        });
    }

    private boolean verifyUser(GatewayUserPrincipal userId) {
        if (!emittersByUserId.containsKey(userId)) return false;
        try { verifier.verify(userId); return true; }
        catch (RuntimeException exception) {
            var leases = emittersByUserId.remove(userId);
            if (leases != null) leases.forEach(lease -> lease.emitter().complete());
            return false;
        }
    }

    private void send(GatewayUserPrincipal userId, Lease lease, SseEmitter.SseEventBuilder event) {
        try {
            if (lease.expiresAt() <= Instant.now().getEpochSecond()) throw new IllegalStateException("expired");
            lease.emitter().send(event);
        } catch (IOException | IllegalStateException exception) {
            remove(userId, lease); lease.emitter().complete();
        }
    }

    private void remove(GatewayUserPrincipal userId, Lease lease) {
        emittersByUserId.computeIfPresent(userId, (ignored, leases) -> {
            leases.remove(lease); return leases.isEmpty() ? null : leases;
        });
    }
    private record Lease(SseEmitter emitter, long expiresAt) { }
}
