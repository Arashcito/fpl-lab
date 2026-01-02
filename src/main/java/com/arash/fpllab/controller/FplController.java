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

    @GetMapping("/api/fpl/gameweeks")
    public List<Gameweek> gameweeks() {
        return fplService.getGameweeks();
    }

    @GetMapping("/api/fpl/positions")
    public List<Position> positions() {
        return fplService.getPositions();
    }

    @GetMapping("/api/fpl/players/{id}")
    public Player playerById(@org.springframework.web.bind.annotation.PathVariable int id) {
        return fplService.getPlayerById(id);
    }


    // example : http://localhost:8080/api/fpl/players/search?q=salah
    @GetMapping("/api/fpl/players/search")
    public List<Player> search(@RequestParam String q) {
        return fplService.searchPlayers(q);
    }

    // example : http://localhost:8080/api/fpl/players/top?limit=10
    @GetMapping("/api/fpl/players/top")
    public List<Player> topPlayers(@RequestParam(defaultValue = "20") int limit) {
        return fplService.getPlayersSorted(limit);
    }

    // Clean players list (optional filters and sorting)
    // Example: /api/fpl/players?teamId=1&positionId=3&sortBy=price&order=asc&limit=10
    @GetMapping("/api/fpl/players")
    public List<Player> players(
            @RequestParam(required = false) Integer teamId,
            @RequestParam(required = false) Integer positionId,
            @RequestParam(defaultValue = "points") String sortBy,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) Integer limit
    ) {
        return fplService.getPlayersSorted(teamId, positionId, sortBy, order, limit);
    }

}
