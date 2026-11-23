package com.arash.fpllab.repository;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BulkWriterTest {

    @Test
    void buildsPostgresUpsert() {
        assertEquals("INSERT INTO t (id, a, b) VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET a = EXCLUDED.a, b = EXCLUDED.b",
                BulkWriter.upsertSql("t", List.of("id", "a", "b")));
    }
}
