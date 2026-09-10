package com.chapchap.customer.domain.customerai.contract;

import com.chapchap.customer.domain.customerai.constant.security.CustomerAiSubjectScope;
import com.chapchap.customer.domain.customerai.controller.security.CustomerAiSubjectJwksController;
import com.chapchap.customer.domain.customerai.request.security.CustomerAiSubjectAssertionRequest;
import com.chapchap.customer.domain.customerai.response.security.CustomerAiSubjectJwksDocument;
import com.chapchap.customer.domain.customerai.service.security.Rs256CustomerAiSubjectAssertionIssuer;

import com.chapchap.customer.global.security.constant.RolePolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

class CustomerContractMaterialExporterTest {
    private static final long USER_ID = 42L;
    private static final long CONSULTATION_ID = 73L;
    private static final UUID REQUEST_ID = UUID.fromString("f3cb52c5-76ce-4f84-83c5-21a225a6d4ae");

    @Test
    void exportsAssertionsAndControllerJwksForTheIsolatedContract() throws Exception {
        Path output = requiredOutputDirectory();
        Material oldMaterial = material("customer-subject-old");
        Material newMaterial = material("customer-subject-new");

        Files.writeString(output.resolve("subject-assertion-old.txt"), oldMaterial.assertion());
        Files.writeString(output.resolve("subject-assertion-new.txt"), newMaterial.assertion());
        writeJson(output.resolve("subject-jwks-old.json"), oldMaterial.document());
        writeJson(output.resolve("subject-jwks-overlap.json"), new CustomerAiSubjectJwksDocument(List.of(
                oldMaterial.document().keys().get(0), newMaterial.document().keys().get(0))));
        writeJson(output.resolve("subject-jwks-new.json"), newMaterial.document());

        Properties context = new Properties();
        context.setProperty("userId", Long.toString(USER_ID));
        context.setProperty("consultationId", Long.toString(CONSULTATION_ID));
        context.setProperty("requestId", REQUEST_ID.toString());
        context.setProperty("scope", CustomerAiSubjectScope.POLICY_READ.value());
        try (var writer = Files.newBufferedWriter(output.resolve("subject-context.properties"))) {
            context.store(writer, null);
        }
    }

    private Material material(String keyId) throws Exception {
        KeyPair pair = keyPair();
        Rs256CustomerAiSubjectAssertionIssuer issuer = new Rs256CustomerAiSubjectAssertionIssuer(
                pair.getPrivate(), keyId, Duration.ofSeconds(60), Clock.system(ZoneId.of("Asia/Seoul")));
        String assertion = issuer.issue(new CustomerAiSubjectAssertionRequest(
                USER_ID,
                RolePolicy.CUSTOMER,
                List.of(CustomerAiSubjectScope.POLICY_READ.value()),
                REQUEST_ID,
                CONSULTATION_ID));
        CustomerAiSubjectJwksDocument document = new CustomerAiSubjectJwksController(
                CustomerAiSubjectJwksDocument.from(keyId, (java.security.interfaces.RSAPublicKey) pair.getPublic()))
                .keys().getBody();
        return new Material(assertion, document);
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

    private static void writeJson(Path target, Object value) throws Exception {
        new ObjectMapper().writeValue(target.toFile(), value);
    }

    private record Material(String assertion, CustomerAiSubjectJwksDocument document) {
    }
}
