package com.chapchap.customer.global.config.customerai;

import com.chapchap.customer.domain.knowledge.dto.processing.async.CustomerAiKnowledgeJobCommand;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AcademyTransportTest {
    @Test
    void allowsOnlyExactHttpOriginsAndRetainsHttpsDefault() {
        var allowed = List.of("http://auth-service:80");
        assertThat(CustomerAiTransportPolicy.allows(URI.create("http://auth-service/jwks"), allowed)).isTrue();
        for (String value : List.of("http://auth-service:81/jwks", "http://auth-service.evil/jwks",
                "http://user@auth-service/jwks", "http://auth-service/jwks#x", "file:///key")) {
            assertThat(CustomerAiTransportPolicy.allows(URI.create(value), allowed)).isFalse();
        }
        assertThat(CustomerAiTransportPolicy.allows(URI.create("http://auth-service/jwks"), List.of())).isFalse();
    }

    @Test
    void sourceHttpRequiresExplicitStorageException() {
        URI url = URI.create("http://minio:9000/bucket/document");
        assertThatThrownBy(() -> new CustomerAiKnowledgeJobCommand.Source(url, "text/plain", 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new CustomerAiKnowledgeJobCommand.Source(url, "text/plain", 4,
                List.of("http://minio:9000"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new CustomerAiKnowledgeJobCommand.Source(url, "text/plain", 4,
                List.of("http://minio:9001"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void academyRequiresEveryAuthenticationAndConsumerFlag() {
        var environment = new MockEnvironment().withProperty("customer.ai.activation.mode", "ACADEMY");
        var gate = new CustomerAiRuntimeActivationGate(environment, new ObjectMapper());
        assertThatThrownBy(gate::verify).isInstanceOf(IllegalStateException.class);
        for (String flag : List.of("internal-auth.enabled", "callback-auth.enabled",
                "knowledge-processing.async-enabled", "consultation-response.async-enabled",
                "consultation-summary.async-enabled")) {
            environment.withProperty("customer.ai." + flag, "true");
        }
        assertThatCode(gate::verify).doesNotThrowAnyException();
        environment.withProperty("customer.ai.internal-auth.enabled", "false");
        assertThatThrownBy(gate::verify).isInstanceOf(IllegalStateException.class);
    }
}
