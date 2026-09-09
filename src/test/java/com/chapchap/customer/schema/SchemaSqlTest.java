package com.chapchap.customer.schema;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaSqlTest {
    @Test
    void manuallyCreatedSchemaMatchesEntitiesAndContainsNoSeedData() throws Exception {
        var dataSource = new DriverManagerDataSource(
                System.getProperty("schema.jdbc.url", "jdbc:h2:mem:schema-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"),
                System.getProperty("schema.jdbc.user", "sa"), "");
        try (var connection = dataSource.getConnection()) {
            try (var existing = connection.getMetaData().getTables(
                    connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                assertThat(existing.next()).as("Schema test requires an empty, isolated database").isFalse();
            }
            var script = new FileSystemResource(Path.of("sql/schema.sql"));
            ScriptUtils.executeSqlScript(connection, script);
            int tableCount = 0;
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    tableCount++;
                    try (var statement = connection.createStatement();
                         var rows = statement.executeQuery("select count(*) from " + tables.getString("TABLE_NAME"))) {
                        rows.next();
                        assertThat(rows.getLong(1)).isZero();
                    }
                }
            }
            assertThat(tableCount).isEqualTo(16);
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.chapchap.customer.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "validate",
                    "hibernate.dialect", "org.hibernate.dialect.MySQLDialect",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            try { factory.afterPropertiesSet(); } finally { factory.destroy(); }
            // Re-running an initial schema must fail instead of hiding existing tables.
            assertThatThrownBy(() -> ScriptUtils.executeSqlScript(connection, script)).isInstanceOf(RuntimeException.class);
        }
    }
}
