package com.arash.fpllab.controller;

import com.arash.fpllab.service.SyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    // Runs a full FPL -> database sync now (it also runs on startup and every 30 minutes).
    // curl -X POST http://localhost:8080/api/sync
    @PostMapping("/api/sync")
    public ResponseEntity<?> sync() {
        SyncService.SyncResult result = syncService.syncAll();
        if (result == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "A sync is already running"));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/sync/status")
    public Map<String, Object> status() {
        var last = syncService.getLastSync();
        return Map.of("lastSync", last == null ? "never" : last.toString());
    }
}
