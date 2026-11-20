package com.arash.fpllab.controller;

import com.arash.fpllab.model.TeamWithStanding;
import com.arash.fpllab.service.StandingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StandingsController {

    private final StandingsService standingsService;

    public StandingsController(StandingsService standingsService) {
        this.standingsService = standingsService;
    }

    // example : http://localhost:8080/api/standings
    @GetMapping("/api/standings")
    public List<TeamWithStanding> getStandings() {
        return standingsService.getStandings();
    }
}
