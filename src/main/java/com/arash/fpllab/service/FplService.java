package com.arash.fpllab.service;

import java.util.Locale; // local language pronunciation


import com.arash.fpllab.client.FplClient;
import com.arash.fpllab.model.Gameweek;
import com.arash.fpllab.model.Player;
import com.arash.fpllab.model.Position;
import com.arash.fpllab.model.Team;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;


@Service
public class FplService {

    private final FplClient fplClient;

    public FplService(FplClient fplClient) {
        this.fplClient = fplClient;
    }

    public JsonNode bootstrap() {
        return fplClient.getBootstrapStatic();
    }

    public List<Team> getTeams() {
        JsonNode root = fplClient.getBootstrapStatic();
        JsonNode teamsNode = root.get("teams");

        List<Team> teams = new ArrayList<>();

        for (JsonNode team : teamsNode) {
            teams.add(
                    new Team(
                            team.get("id").asInt(),
                            team.get("name").asText()
                    )
            );


        }

        return teams;
    }


    public List<Player> getPlayers(Integer teamId, Integer positionId) {
        JsonNode root = fplClient.getBootstrapStatic();
        JsonNode elements = root.get("elements");

        List<Player> players = new ArrayList<>();

        for (JsonNode p : elements) {
            int tId = p.get("id").asInt();
            int posId = p.get("element_type").asInt();

            if (teamId != null && tId != teamId) {
                continue;
            }
            if (positionId != null && posId != positionId) {
                continue;
            }

            players.add(new Player(
                    p.get("id").asInt(),
                    p.get("web_name").asText(),
                    tId,
                    posId,
                    p.get("now_cost").asInt(),
                    p.get("total_points").asInt()
            ));
        }

        return players;
    }

    public List<Gameweek> getGameweeks() {
        var root = bootstrap();
        var events = root.get("events");

        List<Gameweek> result = new ArrayList<>();
        for (var e : events) {
            result.add(new Gameweek(
                    e.get("id").asInt(),
                    e.get("name").asText(),
                    e.get("deadline_time").asText(),
                    e.get("is_current").asBoolean(),
                    e.get("is_next").asBoolean()
            ));
        }
        return result;
    }


    public List<Position> getPositions() {
        var root = bootstrap();
        var types = root.get("element_types");

        List<Position> result = new ArrayList<>();
        for (var t : types) {
            result.add(new Position(
                    t.get("id").asInt(),
                    t.get("singular_name").asText(),
                    t.get("singular_name_short").asText()
            ));

        }
        return result;
    }


    public Player getPlayerById(int playerId) {
        var root = bootstrap();
        var elements = root.get("elements");

        for (var p : elements) {
            if (p.get("id").asInt() == playerId) {
                return new Player(
                        p.get("id").asInt(),
                        p.get("web_name").asText(),
                        p.get("team").asInt(),
                        p.get("element_type").asInt(),
                        p.get("now_cost").asInt(),
                        p.get("total_points").asInt()
                );
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + playerId);

    }

    public List<Player> searchPlayers(String q) {
        String query = q == null ? "" : q.toLowerCase(Locale.ROOT);

        var root = bootstrap();
        var elements = root.get("elements");

        List<Player> results= new ArrayList<>();

        for (var p : elements) {
            String name = p.get("web_name").asText().toLowerCase(Locale.ROOT);
            if (!name.contains(query)) {
                continue;
            }

            results.add(new Player(
                    p.get("id").asInt(),
                    p.get("web_name").asText(),
                    p.get("team").asInt(),
                    p.get("element_type").asInt(),
                    p.get("now_cost").asInt(),
                    p.get("total_points").asInt()
            ));
        }
        return results;
    }

}
