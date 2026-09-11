package com.chapchap.customer.domain.knowledge.dto.processing.async;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiKnowledgeJobCommandTest {
    @Test
    void createsDeterministicIdempotencyKey() {
        assertThat(command(URI.create("https://private-minio.example/object"), 1, "HYBRID_POLICY_V1")
                .idempotencyKey()).isEqualTo("101:HYBRID_POLICY_V1");
    }

    @Test
    void rejectsNonHttpsOrCredentialBearingSourceUrl() {
        assertThatThrownBy(() -> command(
                URI.create("http://private-minio.example/object"), 1, "HYBRID_POLICY_V1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("private-minio.example");
        assertThatThrownBy(() -> command(
                URI.create("https://user:password@private-minio.example/object"), 1, "HYBRID_POLICY_V1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("password");
    }

    @Test
    void rejectsUnsupportedAttemptAndChunkProfile() {
        assertThatThrownBy(() -> command(
                URI.create("https://private-minio.example/object"), 4, "HYBRID_POLICY_V1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
        assertThatThrownBy(() -> command(
                URI.create("https://private-minio.example/object"), 1, "OTHER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkProfile");
    }

    private CustomerAiKnowledgeJobCommand command(URI uri, int attempt, String chunkProfile) {
        return new CustomerAiKnowledgeJobCommand(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                101L,
                attempt,
                new CustomerAiKnowledgeJobCommand.Source(uri, "text/plain", 4L),
                new CustomerAiKnowledgeJobCommand.Metadata(
                        "refund-policy",
                        "subscription-service",
                        "REFUND",
                        "2026.09",
                        OffsetDateTime.parse("2026-09-01T00:00:00+09:00")
                ),
                chunkProfile
        );
    }
}