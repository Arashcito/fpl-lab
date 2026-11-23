package com.arash.fpllab.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeonEnvironmentTest {

    @Test
    void convertsNeonUrlToJdbc() {
        var props = NeonEnvironment.toSpringDatasource(
                "postgresql://neondb_owner:p%40ss@ep-x-123.c-1.us-east-1.aws.neon.tech/neondb?channel_binding=require&sslmode=require");
        assertEquals("jdbc:postgresql://ep-x-123.c-1.us-east-1.aws.neon.tech/neondb?sslmode=require&reWriteBatchedInserts=true",
                props.get("spring.datasource.url"));
        assertEquals("neondb_owner", props.get("spring.datasource.username"));
        assertEquals("p@ss", props.get("spring.datasource.password"));   // URL-encoded characters decoded
    }

    @Test
    void addsSslWhenMissingAndKeepsPort() {
        var props = NeonEnvironment.toSpringDatasource("postgres://u:p@host:6543/db");
        assertEquals("jdbc:postgresql://host:6543/db?sslmode=require&reWriteBatchedInserts=true", props.get("spring.datasource.url"));
    }

    @Test
    void ignoresNonPostgresUrls() {
        assertTrue(NeonEnvironment.toSpringDatasource("mysql://u:p@host/db").isEmpty());
    }

    @Test
    void readsDotenvWithQuotesAndComments(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".env.local");
        Files.writeString(file, """
                # comment
                DATABASE_URL="postgresql://u:p@h/db?sslmode=require"
                AWS_REGION=us-east-1
                export NEON_BRANCH='production'
                """);
        var values = NeonEnvironment.readDotenv(file);
        assertEquals("postgresql://u:p@h/db?sslmode=require", values.get("DATABASE_URL"));
        assertEquals("us-east-1", values.get("AWS_REGION"));
        assertEquals("production", values.get("NEON_BRANCH"));
        assertTrue(NeonEnvironment.readDotenv(dir.resolve("missing")).isEmpty());
    }
}
