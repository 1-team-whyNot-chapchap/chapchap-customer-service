package com.chapchap.customer.domain.notification.service;

import com.chapchap.customer.domain.notification.response.NotificationResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CustomerNotificationSseService {
    private final ConcurrentHashMap<Long, Set<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> emitters = emittersByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ignored -> remove(userId, emitter));
        return emitter;
    }

    public void publish(Long userId, NotificationResponse notification) {
        Set<SseEmitter> emitters = emittersByUserId.getOrDefault(userId, Set.of());
        emitters.forEach(emitter -> send(userId, emitter, notification));
    }

    private void send(Long userId, SseEmitter emitter, NotificationResponse notification) {
        try {
            emitter.send(SseEmitter.event().name("notification").data(notification));
        } catch (IOException | IllegalStateException exception) {
            remove(userId, emitter);
            emitter.complete();
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        emittersByUserId.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
