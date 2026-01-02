package com.arash.fpllab.controller;

import com.arash.fpllab.model.*;
import com.arash.fpllab.service.FplService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;

import com.arash.fpllab.model.Team;

import java.util.List;


@RestController
public class FplController {

    private final FplService fplService;

    public FplController(FplService fplService) {
        this.fplService = fplService;
    }

    @GetMapping("/api/fpl/bootstrap")
    public JsonNode bootstrap() {
        return fplService.bootstrap();
    }

    // clean teams list based on names
    @GetMapping("/api/fpl/teams")
    public List<Team> teams() {
        return fplService.getTeams();
    }

    // Clean players list (optional filters)
    // Example: /api/fpl/players?teamId=1&positionId=3
    @GetMapping("/api/fpl/players")
    public List<Player> players(@RequestParam(required = false) Integer teamId,
                                @RequestParam(required = false) Integer positionId) {

        return fplService.getPlayers(teamId, positionId);
    }

    @GetMapping("/api/fpl/gameweeks")
    public List<Gameweek> gameweeks() {
        return fplService.getGameweeks();
    }

    @GetMapping("/api/fpl/positions")
    public List<Position> positions() {
        return fplService.getPositions();
    }
}
