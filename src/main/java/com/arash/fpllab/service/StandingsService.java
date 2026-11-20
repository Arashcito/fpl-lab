package com.arash.fpllab.service;

import com.arash.fpllab.client.FplClient;
import com.arash.fpllab.model.TeamWithStanding;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the league table from FPL fixture results.
 * FPL's bootstrap-static "teams" block does not carry real standings
 * (points / wins / losses are always 0), so the table is calculated here.
 */
@Service
public class StandingsService {

    private final FplClient fplClient;

    public StandingsService(FplClient fplClient) {
        this.fplClient = fplClient;
    }

    public List<TeamWithStanding> getStandings() {
        JsonNode teams = fplClient.getBootstrapStatic().get("teams");
        JsonNode fixtures = fplClient.getFixtures();
        return calculate(teams, fixtures);
    }

    static List<TeamWithStanding> calculate(JsonNode teams, JsonNode fixtures) {
        Map<Integer, Row> rows = new LinkedHashMap<>();
        for (JsonNode t : teams) {
            int id = t.get("id").asInt();
            rows.put(id, new Row(id, t.get("name").asText(), t.path("short_name").asText()));
        }

        for (JsonNode f : fixtures) {
            if (!isFinished(f)) {
                continue;
            }
            Row home = rows.get(f.get("team_h").asInt());
            Row away = rows.get(f.get("team_a").asInt());
            if (home == null || away == null) {
                continue;
            }
            int homeGoals = f.get("team_h_score").asInt();
            int awayGoals = f.get("team_a_score").asInt();
            home.record(homeGoals, awayGoals);
            away.record(awayGoals, homeGoals);
        }

        List<Row> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator
                .comparingInt(Row::points).reversed()
                .thenComparing(Comparator.comparingInt(Row::goalDifference).reversed())
                .thenComparing(Comparator.comparingInt((Row r) -> r.goalsFor).reversed())
                .thenComparing(r -> r.name));

        List<TeamWithStanding> table = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            table.add(sorted.get(i).toStanding(i + 1));
        }
        return table;
    }

    /**
     * A match counts once the final whistle has gone ("finished_provisional"),
     * without waiting for FPL to confirm bonus points ("finished").
     */
    private static boolean isFinished(JsonNode f) {
        boolean done = f.path("finished").asBoolean(false) || f.path("finished_provisional").asBoolean(false);
        return done && f.path("team_h_score").isNumber() && f.path("team_a_score").isNumber();
    }

    private static final class Row {
        final int teamId;
        final String name;
        final String shortName;
        int won, drawn, lost, goalsFor, goalsAgainst;

        Row(int teamId, String name, String shortName) {
            this.teamId = teamId;
            this.name = name;
            this.shortName = shortName;
        }

        void record(int scored, int conceded) {
            goalsFor += scored;
            goalsAgainst += conceded;
            if (scored > conceded) won++;
            else if (scored == conceded) drawn++;
            else lost++;
        }

        int points() {
            return won * 3 + drawn;
        }

        int goalDifference() {
            return goalsFor - goalsAgainst;
        }

        TeamWithStanding toStanding(int position) {
            return new TeamWithStanding(position, teamId, name, shortName,
                    won + drawn + lost, won, drawn, lost,
                    goalsFor, goalsAgainst, goalDifference(), points());
        }
    }
}
