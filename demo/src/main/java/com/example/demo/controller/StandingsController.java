package com.example.demo.controller;

import com.example.demo.dto.StandingsResponse;
import com.example.demo.dto.StandingGroup;
import com.example.demo.service.StandingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for Premier League standings from football-data.org.
 */
@RestController
@RequestMapping("/api/standings")
public class StandingsController {

    private final StandingsService standingsService;

    public StandingsController(StandingsService standingsService) {
        this.standingsService = standingsService;
    }

    /**
     * Full Premier League standings (TOTAL, HOME, AWAY tables plus competition/season info).
     */
    @GetMapping("/premier-league")
    public ResponseEntity<StandingsResponse> getPremierLeagueStandings() {
        StandingsResponse response = standingsService.getPremierLeagueStandings();
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    /**
     * Premier League main table only (overall standings).
     */
    @GetMapping("/premier-league/table")
    public ResponseEntity<StandingGroup> getPremierLeagueTable() {
        return standingsService.getPremierLeagueTable()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
