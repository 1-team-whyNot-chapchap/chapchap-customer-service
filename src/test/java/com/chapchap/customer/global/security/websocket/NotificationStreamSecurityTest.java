package com.chapchap.customer.global.security.websocket;

import com.chapchap.customer.domain.notification.service.CustomerNotificationSseService;
import com.chapchap.customer.global.security.context.CurrentAccountVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationStreamSecurityTest {
    @Test
    void streamHasFiniteLifetimeAndRemovesChangedAccounts() {
        var verifier = mock(CurrentAccountVerifier.class);
        var streams = new CustomerNotificationSseService(verifier);
        assertThatThrownBy(() -> streams.connect(1L, 1L)).isInstanceOf(AccessDeniedException.class);
        var emitter = streams.connect(1L, Instant.now().plusSeconds(60).getEpochSecond());
        assertThat(emitter.getTimeout()).isBetween(1L, 60000L);
        doThrow(new BadCredentialsException("changed role")).when(verifier).verify(any());
        streams.heartbeat();
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(streams, "emittersByUserId")).isEmpty();
    }
}
