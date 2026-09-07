package com.chapchap.customer.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.ObjectMapper;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RuntimeConfigurationTest {
    @TempDir
    Path directory;

    @Test
    void exampleCoversEveryYamlEnvironmentVariableWithoutDuplicateKeys() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));
        String example = Files.readString(Path.of(".env.example"));
        Properties values = new Properties();
        values.load(new StringReader(example));
        Set<String> placeholders = new HashSet<>();
        Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)(?=[:}])").matcher(yaml)
                .results().forEach(match -> placeholders.add(match.group(1)));
        assertThat(values.stringPropertyNames()).containsAll(placeholders);
        long declarations = example.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).count();
        assertThat(declarations).isEqualTo(values.size());
        assertThat(values.getProperty("DB_PASSWORD")).isEqualTo(values.getProperty("MYSQL_PASSWORD"));
    }

    @Test
    void realMainYamlAndExampleBindWithoutActivatingExternalAiRuntime() throws Exception {
        StandardEnvironment environment = load(Files.readString(Path.of(".env.example")), Map.of());
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8084);
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://localhost:3307/customer_db");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.kafka.listener.auto-startup", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("10MB");
        assertThat(environment.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("11MB");
        KnowledgeStorageProperties storage = Binder.get(environment)
                .bind("customer.storage.knowledge", KnowledgeStorageProperties.class)
                .orElseThrow(IllegalStateException::new);
        assertThat(storage.getAccessKey()).isEqualTo("change-me-minio-access-key");
        assertThat(storage.getBucket()).isEqualTo("customer-knowledge");
        assertThatCode(() -> new CustomerAiRuntimeActivationGate(environment, new ObjectMapper()).verify())
                .doesNotThrowAnyException();
    }

    @Test
    void exportedVariablesOverrideDotEnvAndPemEscapesLoadAsNewlines() throws Exception {
        StandardEnvironment environment = load("APP_PORT=8084\nDB_PASSWORD=example-only\n"
                + "CUSTOMER_AI_SUBJECT_PUBLIC_KEY_PEM=first\\nsecond\n",
                Map.of("APP_PORT", "18084", "DB_PASSWORD", "exported-test-value"));
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(18084);
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("exported-test-value");
        assertThat(environment.getProperty("customer.ai.internal-auth.subject-public-key-pem"))
                .isEqualTo("first\nsecond");
    }

    private StandardEnvironment load(String example, Map<String, Object> exported) throws Exception {
        Path envFile = directory.resolve(".env");
        Files.writeString(envFile, example);
        Path sourceYaml = Path.of("src/main/resources/application.yaml").toAbsolutePath();
        var yaml = new YamlPropertySourceLoader().load("main", new FileSystemResource(sourceYaml));
        assertThat(yaml.getFirst().getProperty("spring.config.import"))
                .isEqualTo("optional:file:.env[.properties]");
        Path mainYaml = directory.resolve("application.yaml");
        Files.writeString(mainYaml, Files.readString(sourceYaml).replace(
                "optional:file:.env[.properties]", "optional:" + envFile.toUri() + "[.properties]"));
        StandardEnvironment environment = new StandardEnvironment();
        // Never load the developer's real .env or inherit credentials in tests.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("config-location", Map.of(
                "spring.config.location", mainYaml.toUri().toString()
        )));
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("exported", exported));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }
}
