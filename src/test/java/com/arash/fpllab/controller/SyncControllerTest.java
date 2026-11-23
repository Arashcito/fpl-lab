package com.arash.fpllab.controller;

import com.arash.fpllab.service.SyncService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncControllerTest {

    private final SyncService syncService = mock(SyncService.class);
    private final SyncService.SyncResult result = new SyncService.SyncResult(20, 600, 380, 1, 100, Instant.now());

    @Test
    void openWhenNoTokenIsConfigured() {
        when(syncService.syncAll()).thenReturn(result);
        ResponseEntity<?> response = new SyncController(syncService, "").sync(null, null);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void rejectsMissingOrWrongToken() {
        var controller = new SyncController(syncService, "secret");
        assertEquals(401, controller.sync(null, null).getStatusCode().value());
        assertEquals(401, controller.sync("wrong", null).getStatusCode().value());
        verify(syncService, never()).syncAll();
    }

    @Test
    void acceptsTokenInHeaderOrQuery() {
        when(syncService.syncAll()).thenReturn(result);
        var controller = new SyncController(syncService, "secret");
        assertEquals(200, controller.sync("secret", null).getStatusCode().value());
        assertEquals(200, controller.sync(null, "secret").getStatusCode().value());
    }

    @Test
    void syncAlreadyRunningIsNotAnError() {
        when(syncService.syncAll()).thenReturn(null);
        assertEquals(202, new SyncController(syncService, "").sync(null, null).getStatusCode().value());
    }
}
