package com.arash.fpllab.controller;

import com.arash.fpllab.dto.PlayerDbView;
import com.arash.fpllab.dto.PlayerView;
import com.arash.fpllab.entity.PlayerEntity;
import com.arash.fpllab.entity.TeamEntity;
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
    // limit=10
    // Test examples
    // Example: /api/fpl/players?teamId=1&positionId=3&sortBy=price&order=asc&
    //Top 20 by points (default):
    //http://localhost:8080/api/fpl/players
    //Cheapest 30 Arsenal players:
    //http://localhost:8080/api/fpl/players?teamId=1&sortBy=price&order=asc&limit=30
    //A–Z midfielders:
    //http://localhost:8080/api/fpl/players?positionId=3&sortBy=name&order=asc
    @GetMapping("/api/fpl/players")
    public List<PlayerView> players(
            @RequestParam(required = false) Integer teamId,
            @RequestParam(required = false) Integer positionId,
            @RequestParam(defaultValue = "points") String sortBy,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) Integer limit
    ) {
        return fplService.getPlayerViews(teamId, positionId, sortBy, order, limit);
    }

    @GetMapping("api/db/sync/teams")
    public String syncTeams() {
        int saved = fplService.syncTeamsToDB();
        return "Saved: " + saved + " teams to DB";
    }

    @GetMapping("api/db/teams")
    public List<TeamEntity> dbTeams() {
        return fplService.getTeamsFromDb();
    }

    @GetMapping("/api/db/sync/players")
    public String syncPlayers() {
        int saved = fplService.syncPlayersToDb();
        return "Saved " + saved + " players to DB ✅";
    }

    @GetMapping("/api/db/players")
    public List<PlayerEntity> dbPlayers() {
        return fplService.getPlayersFromDb();
    }


    // example  : http://localhost:8080/api/db/players/view?limit=20
    @GetMapping("/api/db/players/view")
    public List<PlayerDbView> dbPlayersView(@RequestParam(required = false) Integer limit) {
        return fplService.getPlayerViewsFromDb(limit);
    }




}
