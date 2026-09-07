package com.chapchap.auth.global.security.servicejwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

class AuthContractMaterialExporterTest {
    private static final String CLIENT_SECRET = "isolated-customer-service-secret-32";

    @Test
    void exportsTokensAndControllerJwksForTheIsolatedContract() throws Exception {
        Path output = requiredOutputDirectory();
        Material oldMaterial = material("auth-old");
        Material newMaterial = material("auth-new");

        writeSecret(output.resolve("service-token-old.txt"), oldMaterial.token());
        writeSecret(output.resolve("service-token-new.txt"), newMaterial.token());
        writeJson(output.resolve("service-jwks-old.json"), oldMaterial.document());
        writeJson(output.resolve("service-jwks-overlap.json"), new JwksDocument(List.of(
                oldMaterial.document().keys().get(0), newMaterial.document().keys().get(0))));
        writeJson(output.resolve("service-jwks-new.json"), newMaterial.document());
    }

    private Material material(String keyId) throws Exception {
        KeyPair pair = keyPair();
        InternalServiceJwtProperties properties = new InternalServiceJwtProperties(
                true,
                "chapchap-auth-service",
                keyId,
                300,
                pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                Map.of("customer-service", new InternalServiceJwtProperties.Client(
                        CLIENT_SECRET,
                        "customer-service",
                        Set.of("chapchap-customer-ai"),
                        Set.of("customer-ai.invoke"))));
        RsaServiceKeyMaterial keys = RsaServiceKeyMaterial.fromPem(
                properties.privateKeyPem(), properties.publicKeyPem());
        ServiceTokenIssuer issuer = new ServiceTokenIssuer(
                properties,
                keys,
                new ServiceClientRegistry(properties.clients()),
                Clock.system(ZoneId.of("Asia/Seoul")));
        ServiceTokenResponse response = issuer.issue(
                "client_credentials",
                "customer-service",
                CLIENT_SECRET,
                "chapchap-customer-ai",
                "customer-ai.invoke");
        JwksDocument document = new JwksController(JwksDocument.from(keyId, keys.publicKey()))
                .keys().getBody();
        return new Material(response.accessToken(), document);
    }

    private static Path requiredOutputDirectory() throws Exception {
        String value = System.getProperty("contract.outputDir");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("contract.outputDir is required.");
        }
        return Files.createDirectories(Path.of(value).toAbsolutePath().normalize());
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static void writeSecret(Path target, String value) throws Exception {
        Files.writeString(target, value);
    }

    private static void writeJson(Path target, Object value) throws Exception {
        new ObjectMapper().writeValue(target.toFile(), value);
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }

    private record Material(String token, JwksDocument document) {
    }
}
