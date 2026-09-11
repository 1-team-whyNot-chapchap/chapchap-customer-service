package com.chapchap.customer;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.global.security.context.CurrentAccountVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "customer.storage.quality-inquiry.access-key=test-access-key",
        "customer.storage.quality-inquiry.secret-key=test-secret-key"
})
@Import(ChapchapCustomerServiceApplicationTests.SecurityProbeConfiguration.class)
class CustomerRealtimeIntegrationTest {
    @LocalServerPort int port;
    @Autowired ConsultationRepository consultations;
    @Autowired SimpMessagingTemplate messaging;
    @MockitoBean CurrentAccountVerifier verifier;
    @Autowired com.chapchap.customer.domain.notification.service.CustomerNotificationSseService streams;

    @Test
    void actualSseStreamsNotificationsAndClosesAfterRoleChange() throws Exception {
        try (var client = HttpClient.newHttpClient(); var executor = Executors.newSingleThreadExecutor()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/customer/notifications/stream"))
                    .header("X-User-Id", "7").header("X-User-Role", "CUSTOMER")
                    .header("X-User-Expires-At", Long.toString(Instant.now().plusSeconds(60).getEpochSecond())).GET().build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                assertThat(executor.submit(() -> readEvent(reader)).get(5, TimeUnit.SECONDS)).contains("event:ready");
                streams.publish(7L, new com.chapchap.customer.domain.notification.response.NotificationResponse(
                        1L, com.chapchap.customer.domain.notification.constant.NotificationType.values()[0],
                        "integration-notification", "content", "CONSULTATION", "3", LocalDateTime.now(), false));
                assertThat(executor.submit(() -> readEvent(reader)).get(5, TimeUnit.SECONDS))
                        .contains("event:notification", "integration-notification");
                doThrow(new BadCredentialsException("role changed")).when(verifier).verify(any());
                streams.heartbeat();
                assertThat(executor.submit(() -> readEvent(reader)).get(5, TimeUnit.SECONDS)).isNull();
            }
        }
    }

    private static String readEvent(java.io.BufferedReader reader) throws java.io.IOException {
        StringBuilder event = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) return event.toString();
            event.append(line);
        }
        return null;
    }


    @Test
    void actualSocketReceivesMessagesThenClosesWhenAccountIsSuspended() throws Exception {
        Consultation consultation = Consultation.create(7L, LocalDateTime.now());
        ReflectionTestUtils.setField(consultation, "id", 3L);
        when(consultations.findById(3L)).thenReturn(Optional.of(consultation));
        var frames = new LinkedBlockingQueue<String>();
        var closed = new CompletableFuture<Integer>();
        var listener = new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();
            public void onOpen(WebSocket socket) { socket.request(1); }
            public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) { frames.add(buffer.toString()); buffer.setLength(0); }
                socket.request(1); return null;
            }
            public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
                closed.complete(code); return null;
            }
        };
        try (var client = HttpClient.newHttpClient()) {
            var socket = client.newWebSocketBuilder().header("Origin", "http://localhost:5173")
                    .header("X-User-Id", "7").header("X-User-Role", "CUSTOMER")
                    .header("X-User-Expires-At", Long.toString(Instant.now().plusSeconds(60).getEpochSecond()))
                    .subprotocols("v12.stomp")
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/customer/consultations"), listener)
                    .get(5, TimeUnit.SECONDS);
            try {
                socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true).join();
                assertThat(frames.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
                socket.sendText("SUBSCRIBE\nid:one\ndestination:/topic/consultations/3\n\n\0", true).join();
                String received = null;
                for (int attempt = 0; attempt < 20 && received == null; attempt++) {
                    messaging.convertAndSend("/topic/consultations/3", (Object) Map.of("content", "integration-message"));
                    received = frames.poll(100, TimeUnit.MILLISECONDS);
                }
                assertThat(received).contains("MESSAGE", "integration-message");
                frames.clear();
                doThrow(new BadCredentialsException("suspended")).when(verifier).verify(any());
                messaging.convertAndSend("/topic/consultations/3", (Object) Map.of("content", "must-not-deliver"));
                assertThat(closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
                assertThat(frames).noneMatch(frame -> frame.contains("must-not-deliver"));
            } finally { socket.abort(); }
        }
    }
}
