package com.chapchap.customer.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerAiBilateralContractMatrixTest {
    private static final String PROVIDER_COMMIT = "6e2d036a46be01030e55c5a0cde261916bfb28c7";
    private static final String CONSUMER_COMMIT = "3f53d25acbef498eea24aede8e4ed4cc2b5d2e5f";
    private static final String FIXTURE_SHA256 =
            "ABE26EB47D4D509785FE2E1C9BA5345D2FE279B1AA0C73D0CAB19A4E38B78862";
    private static final Pattern GIT_COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Set<String> MATRIX_IDS = Set.of(
            "SERVICE_JWT",
            "SUBJECT_ASSERTION",
            "CONSULTATION",
            "KNOWLEDGE",
            "SUMMARY",
            "ERROR_HANDLING",
            "OBSERVABILITY",
            "RAG_CURRENT_STATE"
    );
    private static final Set<String> BLOCKER_CODES = Set.of(
            "AUTH_JWKS_UNAVAILABLE",
            "PRIVATE_MINIO_UNAVAILABLE",
            "CHROMA_UNAVAILABLE",
            "CALLBACK_AUTH_UNCONFIRMED",
            "MCP_TRANSPORT_UNCONFIRMED",
            "TRACE_METRIC_BACKEND_UNCONFIRMED",
            "DOMAIN_READ_ENDPOINTS_UNCONFIRMED",
            "ACTIVE_KNOWLEDGE_CONTRACT_UNCONFIRMED",
            "ISOLATED_ENVIRONMENT_UNAVAILABLE"
    );

    private static JsonNode matrix;

    @BeforeAll
    static void loadMatrix() throws Exception {
        try (InputStream input = CustomerAiBilateralContractMatrixTest.class.getResourceAsStream(
                "/contracts/customer-ai-bilateral-matrix-v1.json")) {
            assertThat(input).as("bilateral contract matrix must exist").isNotNull();
            matrix = new ObjectMapper().readTree(input);
        }
    }

    @Test
    void pinsTheVerifiedCommitPairAndFixtureFingerprint() throws Exception {
        assertExactFields(matrix,
                "schemaVersion", "contractStatus", "activationDecision", "verifiedPair", "fixture",
                "runtimePrerequisites", "runtimeActivation", "rollbackPolicy", "matrix");
        assertThat(matrix.required("schemaVersion").textValue()).isEqualTo("1.0");
        assertThat(matrix.required("contractStatus").textValue()).isEqualTo("CANDIDATE");
        assertThat(matrix.required("activationDecision").textValue()).isEqualTo("BLOCKED");

        JsonNode pair = matrix.required("verifiedPair");
        assertExactFields(pair, "provider", "consumer");
        assertRepository(pair.required("provider"), "Customer-Ai", PROVIDER_COMMIT);
        assertRepository(pair.required("consumer"), "chapchap-customer-service", CONSUMER_COMMIT);

        JsonNode fixture = matrix.required("fixture");
        assertExactFields(fixture, "schemaVersion", "sha256");
        assertThat(fixture.required("schemaVersion").textValue()).isEqualTo("1.0");
        assertThat(fixture.required("sha256").textValue()).isEqualTo(FIXTURE_SHA256);
        assertThat(candidateFixtureSha256()).isEqualTo(FIXTURE_SHA256);
    }

    @Test
    void coversEveryBilateralMatrixRowWithClosedStatusesAndBlockers() {
        Set<String> actualIds = new HashSet<>();
        for (JsonNode row : matrix.required("matrix")) {
            assertExactFields(row, "id", "provider", "consumer", "isolatedIntegration", "blockers",
                    "providerEvidence", "consumerEvidence");
            actualIds.add(row.required("id").textValue());
            assertThat(row.required("provider").textValue()).isEqualTo("PASS");
            assertThat(row.required("consumer").textValue()).isEqualTo("PASS");
            String isolatedIntegration = row.required("isolatedIntegration").textValue();
            assertThat(isolatedIntegration).isIn("PASS", "BLOCKED");
            if ("PASS".equals(isolatedIntegration)) {
                assertThat(row.required("blockers")).isEmpty();
            } else {
                assertThat(row.required("blockers")).isNotEmpty().allSatisfy(blocker ->
                        assertThat(blocker.textValue()).isIn(BLOCKER_CODES));
            }
            assertEvidence(row.required("providerEvidence"));
            assertEvidence(row.required("consumerEvidence"));
        }
        assertThat(actualIds).containsExactlyInAnyOrderElementsOf(MATRIX_IDS);
    }

    @Test
    void recordsOnlyActuallyExecutedAuthenticationRowsAsIsolatedPass() {
        Set<String> isolatedPassRows = new HashSet<>();
        for (JsonNode row : matrix.required("matrix")) {
            if ("PASS".equals(row.required("isolatedIntegration").textValue())) {
                isolatedPassRows.add(row.required("id").textValue());
            }
        }

        assertThat(isolatedPassRows).containsExactlyInAnyOrder("SERVICE_JWT", "SUBJECT_ASSERTION");
    }

    @Test
    void keepsEveryRuntimePrerequisiteAndCandidateComponentBlocked() {
        JsonNode prerequisites = matrix.required("runtimePrerequisites");
        assertExactFields(prerequisites, "authJwks", "privateMinio", "chroma",
                "callbackAuthentication", "mcpTransport", "traceMetricBackend");
        prerequisites.properties().forEach(entry -> assertThat(entry.getValue().textValue()).isEqualTo("BLOCKED"));

        JsonNode activation = matrix.required("runtimeActivation");
        assertExactFields(activation, "providerRoutersMounted", "consumerClientsRegistered");
        assertThat(activation.required("providerRoutersMounted").booleanValue()).isFalse();
        assertThat(activation.required("consumerClientsRegistered").booleanValue()).isFalse();
        assertThat(matrix.required("rollbackPolicy").textValue())
                .isEqualTo("KEEP_CANDIDATE_ROUTES_UNMOUNTED_AND_CONSUMERS_UNREGISTERED");
        assertThat(isActivationReady(matrix)).isFalse();
    }

    @Test
    void requiresAllEvidenceBeforeActivationCanBeApproved() {
        ObjectNode approved = (ObjectNode) matrix.deepCopy();
        approved.put("contractStatus", "CONFIRMED");
        approved.put("activationDecision", "APPROVED");
        approved.required("runtimePrerequisites").properties()
                .forEach(entry -> ((ObjectNode) approved.required("runtimePrerequisites"))
                        .put(entry.getKey(), "PASS"));
        ObjectNode activation = (ObjectNode) approved.required("runtimeActivation");
        activation.put("providerRoutersMounted", true);
        activation.put("consumerClientsRegistered", true);
        approved.required("matrix").forEach(row -> {
            ((ObjectNode) row).put("isolatedIntegration", "PASS");
            ((ObjectNode) row).putArray("blockers");
        });
        assertThat(isActivationReady(approved)).isTrue();

        ObjectNode missingIntegration = approved.deepCopy();
        ((ObjectNode) missingIntegration.required("matrix").get(0))
                .put("isolatedIntegration", "BLOCKED");
        assertThat(isActivationReady(missingIntegration)).isFalse();

        ObjectNode missingPrerequisite = approved.deepCopy();
        ((ObjectNode) missingPrerequisite.required("runtimePrerequisites"))
                .put("authJwks", "BLOCKED");
        assertThat(isActivationReady(missingPrerequisite)).isFalse();

        ObjectNode consumerDisabled = approved.deepCopy();
        ((ObjectNode) consumerDisabled.required("runtimeActivation"))
                .put("consumerClientsRegistered", false);
        assertThat(isActivationReady(consumerDisabled)).isFalse();
    }

    private static void assertRepository(JsonNode repository, String expectedName, String expectedCommit) {
        assertExactFields(repository, "repository", "commit");
        assertThat(repository.required("repository").textValue()).isEqualTo(expectedName);
        assertThat(repository.required("commit").textValue())
                .matches(GIT_COMMIT)
                .isEqualTo(expectedCommit);
    }

    private static void assertEvidence(JsonNode evidence) {
        assertThat(evidence.isArray()).isTrue();
        assertThat(evidence).isNotEmpty().allSatisfy(item ->
                assertThat(item.textValue()).matches("[A-Za-z0-9_./-]+"));
    }

    private static String candidateFixtureSha256() throws Exception {
        try (InputStream input = CustomerAiBilateralContractMatrixTest.class.getResourceAsStream(
                "/contracts/customer-ai-candidate-v1.json")) {
            assertThat(input).as("candidate fixture must exist").isNotNull();
            return java.util.HexFormat.of().withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }

    private static boolean isActivationReady(JsonNode candidate) {
        if (!"CONFIRMED".equals(candidate.required("contractStatus").textValue())
                || !"APPROVED".equals(candidate.required("activationDecision").textValue())) {
            return false;
        }
        boolean prerequisitesPass = true;
        for (JsonNode prerequisite : candidate.required("runtimePrerequisites")) {
            prerequisitesPass &= "PASS".equals(prerequisite.textValue());
        }
        JsonNode activation = candidate.required("runtimeActivation");
        boolean componentsEnabled = activation.required("providerRoutersMounted").booleanValue()
                && activation.required("consumerClientsRegistered").booleanValue();
        boolean rowsPass = candidate.required("matrix").isArray()
                && candidate.required("matrix").size() == MATRIX_IDS.size();
        for (JsonNode row : candidate.required("matrix")) {
            rowsPass &= "PASS".equals(row.required("provider").textValue())
                    && "PASS".equals(row.required("consumer").textValue())
                    && "PASS".equals(row.required("isolatedIntegration").textValue())
                    && row.required("blockers").isEmpty();
        }
        return prerequisitesPass && componentsEnabled && rowsPass;
    }

    private static void assertExactFields(JsonNode node, String... expected) {
        assertThat(node.isObject()).isTrue();
        Set<String> actual = new HashSet<>();
        actual.addAll(node.propertyNames());
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }
}
