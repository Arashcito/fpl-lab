package com.arash.fpllab.controller;

import com.arash.fpllab.service.SyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
public class SyncController {

    private final SyncService syncService;
    private final String syncToken;

    public SyncController(SyncService syncService, @Value("${fpl.sync.token:}") String syncToken) {
        this.syncService = syncService;
        this.syncToken = syncToken;
    }

    /**
     * Runs a full FPL -> database sync now (it also runs on startup and every 30 minutes).
     * When SYNC_TOKEN is set (online), the same token must be sent in the X-Sync-Token header
     * or ?token= so strangers can't trigger it. Locally (no token set) it is open:
     *   curl -X POST http://localhost:8080/api/sync
     */
    @PostMapping("/api/sync")
    public ResponseEntity<?> sync(@RequestHeader(value = "X-Sync-Token", required = false) String headerToken,
                                  @RequestParam(value = "token", required = false) String queryToken) {
        if (!authorised(headerToken != null ? headerToken : queryToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Missing or wrong sync token"));
        }
        SyncService.SyncResult result = syncService.syncAll();
        if (result == null) {
            // e.g. the startup sync after waking up is still running; not an error
            return ResponseEntity.accepted().body(Map.of("message", "A sync is already running"));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/sync/status")
    public Map<String, Object> status() {
        var last = syncService.getLastSync();
        return Map.of("lastSync", last == null ? "never" : last.toString());
    }

    private boolean authorised(String given) {
        if (syncToken == null || syncToken.isBlank()) {
            return true;
        }
        if (given == null) {
            return false;
        }
        // constant-time comparison so the token can't be guessed from response timing
        return MessageDigest.isEqual(syncToken.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
