package com.chapchap.customer.global.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerAiActivationManifestVerifierTest {
    private final CustomerAiActivationManifestVerifier verifier = new CustomerAiActivationManifestVerifier();

    @Test
    void rejectsCurrentBlockedManifest() throws Exception {
        assertThat(verifier.isReady(loadManifest())).isFalse();
    }

    @Test
    void acceptsOnlyCompleteApprovedEvidence() throws Exception {
        ObjectNode approved = (ObjectNode) loadManifest().deepCopy();
        approved.put("contractStatus", "CONFIRMED");
        approved.put("activationDecision", "APPROVED");
        approved.required("runtimePrerequisites").properties()
                .forEach(entry -> ((ObjectNode) approved.required("runtimePrerequisites"))
                        .put(entry.getKey(), "PASS"));
        ((ObjectNode) approved.required("runtimeActivation")).put("providerRoutersMounted", true);
        ((ObjectNode) approved.required("runtimeActivation")).put("consumerClientsRegistered", true);
        approved.required("matrix").forEach(row -> {
            ((ObjectNode) row).put("isolatedIntegration", "PASS");
            ((ObjectNode) row).putArray("blockers");
        });

        assertThat(verifier.isReady(approved)).isTrue();

        ((ObjectNode) approved.required("matrix").get(0)).put("isolatedIntegration", "BLOCKED");
        assertThat(verifier.isReady(approved)).isFalse();
    }

    private JsonNode loadManifest() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/contracts/customer-ai-bilateral-matrix-v1.json")) {
            assertThat(input).isNotNull();
            return new ObjectMapper().readTree(input);
        }
    }
}
