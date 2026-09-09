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
    private final ConcurrentHashMap<Long, Set<Lease>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId, long expiresAt) {
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
        if (!verifyUser(userId)) return;
        emittersByUserId.getOrDefault(userId, Set.of()).forEach(lease ->
                send(userId, lease, SseEmitter.event().name("notification").data(notification)));
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        emittersByUserId.forEach((userId, leases) -> {
            if (verifyUser(userId)) leases.forEach(lease -> send(userId, lease, SseEmitter.event().comment("heartbeat")));
        });
    }

    private boolean verifyUser(Long userId) {
        if (!emittersByUserId.containsKey(userId)) return false;
        try { verifier.verify(new GatewayUserPrincipal(userId.toString(), RolePolicy.CUSTOMER)); return true; }
        catch (RuntimeException exception) {
            var leases = emittersByUserId.remove(userId);
            if (leases != null) leases.forEach(lease -> lease.emitter().complete());
            return false;
        }
    }

    private void send(Long userId, Lease lease, SseEmitter.SseEventBuilder event) {
        try {
            if (lease.expiresAt() <= Instant.now().getEpochSecond()) throw new IllegalStateException("expired");
            lease.emitter().send(event);
        } catch (IOException | IllegalStateException exception) {
            remove(userId, lease); lease.emitter().complete();
        }
    }

    private void remove(Long userId, Lease lease) {
        emittersByUserId.computeIfPresent(userId, (ignored, leases) -> {
            leases.remove(lease); return leases.isEmpty() ? null : leases;
        });
    }
    private record Lease(SseEmitter emitter, long expiresAt) { }
}
