package com.arash.fpllab.service;

import com.arash.fpllab.client.FplClient;
import com.arash.fpllab.model.Player;
import com.arash.fpllab.model.Team;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;

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




}
