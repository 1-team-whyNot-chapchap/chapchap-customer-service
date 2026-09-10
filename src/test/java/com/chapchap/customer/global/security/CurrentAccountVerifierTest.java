package com.chapchap.customer.global.security;

import com.chapchap.customer.global.security.context.CurrentAccountVerifier;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;

import com.chapchap.customer.global.security.context.*;
import com.chapchap.customer.global.security.constant.RolePolicy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class CurrentAccountVerifierTest {
    @Test
    void verifiesCurrentIdentityAndFailsClosedOnChangedOrInvalidResponses() throws Exception {
        var body = new AtomicReference<>("{\"code\":\"00\",\"message\":\"ok\",\"data\":{\"userId\":\"1\",\"name\":\"admin\",\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}}");
        var receivedId = new AtomicReference<String>();
        var receivedRole = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/me", exchange -> {
            receivedId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            receivedRole.set(exchange.getRequestHeaders().getFirst("X-User-Role"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var verifier = new CurrentAccountVerifier("http://127.0.0.1:" + server.getAddress().getPort());
            var principal = new GatewayUserPrincipal("1", RolePolicy.ADMIN);
            verifier.verify(principal);
            assertThat(receivedId.get()).isEqualTo("1");
            assertThat(receivedRole.get()).isEqualTo("ADMIN");
            String valid = body.get();
            for (String invalid : new String[]{valid.replace("ACTIVE", "SUSPENDED"),
                    valid.replace("ADMIN", "CUSTOMER"), valid.replace("\"1\"", "\"2\""),
                    "{\"code\":\"00\",\"data\":null}", "{}", "not json"}) {
                body.set(invalid);
                assertThatThrownBy(() -> verifier.verify(principal)).isInstanceOf(BadCredentialsException.class);
            }
            server.stop(0);
            assertThatThrownBy(() -> verifier.verify(principal)).isInstanceOf(BadCredentialsException.class);
        } finally { server.stop(0); }
    }
}
