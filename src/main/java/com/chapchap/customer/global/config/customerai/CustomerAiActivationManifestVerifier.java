package com.chapchap.customer.global.config.customerai;

import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

public final class CustomerAiActivationManifestVerifier {
    private static final Set<String> PREREQUISITES = Set.of(
            "authJwks",
            "privateMinio",
            "chroma",
            "callbackAuthentication",
            "mcpTransport",
            "structuredLogBackend"
    );
    private static final Set<String> MATRIX_ROWS = Set.of(
            "SERVICE_JWT",
            "SUBJECT_ASSERTION",
            "CONSULTATION",
            "KNOWLEDGE",
            "SUMMARY",
            "ERROR_HANDLING",
            "OBSERVABILITY",
            "RAG_CURRENT_STATE"
    );
    private static final String ROLLBACK_POLICY =
            "KEEP_CANDIDATE_ROUTES_UNMOUNTED_AND_CONSUMERS_UNREGISTERED";

    public boolean isReady(JsonNode manifest) {
        try {
            return hasApprovedContract(manifest)
                    && allPrerequisitesPass(manifest.required("runtimePrerequisites"))
                    && allComponentsEnabled(manifest.required("runtimeActivation"))
                    && allMatrixRowsPass(manifest.required("matrix"))
                    && ROLLBACK_POLICY.equals(manifest.required("rollbackPolicy").textValue());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean hasApprovedContract(JsonNode manifest) {
        return manifest != null
                && "1.0".equals(manifest.required("schemaVersion").textValue())
                && "CONFIRMED".equals(manifest.required("contractStatus").textValue())
                && "APPROVED".equals(manifest.required("activationDecision").textValue());
    }

    private boolean allPrerequisitesPass(JsonNode prerequisites) {
        if (!prerequisites.isObject() || !fieldNames(prerequisites).equals(PREREQUISITES)) {
            return false;
        }
        for (String prerequisite : PREREQUISITES) {
            if (!"PASS".equals(prerequisites.required(prerequisite).textValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean allComponentsEnabled(JsonNode activation) {
        return activation.isObject()
                && fieldNames(activation).equals(Set.of("providerRoutersMounted", "consumerClientsRegistered"))
                && activation.required("providerRoutersMounted").asBoolean()
                && activation.required("consumerClientsRegistered").asBoolean();
    }

    private boolean allMatrixRowsPass(JsonNode matrix) {
        if (!matrix.isArray() || matrix.size() != MATRIX_ROWS.size()) {
            return false;
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode row : matrix) {
            ids.add(row.required("id").textValue());
            if (!"PASS".equals(row.required("provider").textValue())
                    || !"PASS".equals(row.required("consumer").textValue())
                    || !"PASS".equals(row.required("isolatedIntegration").textValue())
                    || !row.required("blockers").isEmpty()) {
                return false;
            }
        }
        return ids.equals(MATRIX_ROWS);
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return Set.copyOf(names);
    }
}
