package com.example.demo.service;

import com.example.demo.client.FootballDataClient;
import com.example.demo.dto.StandingsResponse;
import com.example.demo.dto.StandingGroup;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Provides Premier League standings, using the "TOTAL" table by default.
 */
@Service
public class StandingsService {

    private final FootballDataClient footballDataClient;

    public StandingsService(FootballDataClient footballDataClient) {
        this.footballDataClient = footballDataClient;
    }

    /**
     * Full response from the API (competition, season, all standing types: TOTAL, HOME, AWAY).
     */
    public StandingsResponse getPremierLeagueStandings() {
        return footballDataClient.getPremierLeagueStandings();
    }

    /**
     * The main league table (TOTAL standings only).
     */
    public Optional<StandingGroup> getPremierLeagueTable() {
        StandingsResponse response = footballDataClient.getPremierLeagueStandings();
        if (response == null || response.standings() == null) {
            return Optional.empty();
        }
        return response.standings().stream()
                .filter(s -> "TOTAL".equals(s.type()))
                .findFirst();
    }
}
