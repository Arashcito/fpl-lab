package com.arash.fpllab.service;

import com.arash.fpllab.client.FootballDataClient;
import com.arash.fpllab.client.FplClient;
import com.arash.fpllab.model.TeamWithStanding;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the Premier League table.
 * Primary source: football-data.org live standings (needs FOOTBALL_DATA_API_KEY).
 * Fallback: calculated from FPL fixture results, because FPL's bootstrap-static
 * "teams" block does not carry real standings (points / wins / losses are always 0).
 */
@Service
public class StandingsService {

    private static final Logger logger = LoggerFactory.getLogger(StandingsService.class);

    private final FplClient fplClient;
    private final FootballDataClient footballDataClient;

    public StandingsService(FplClient fplClient, FootballDataClient footballDataClient) {
        this.fplClient = fplClient;
        this.footballDataClient = footballDataClient;
    }

    public List<TeamWithStanding> getStandings() {
        if (footballDataClient.isConfigured()) {
            try {
                List<TeamWithStanding> live = fromFootballData(footballDataClient.getStandings());
                if (!live.isEmpty()) {
                    return live;
                }
                logger.warn("football-data.org returned no table; using FPL fixtures instead");
            } catch (Exception e) {
                logger.warn("football-data.org request failed ({}); using FPL fixtures instead", e.getMessage());
            }
        }
        JsonNode teams = fplClient.getBootstrapStatic().get("teams");
        JsonNode fixtures = fplClient.getFixtures();
        return calculate(teams, fixtures);
    }

    /**
     * Maps the "TOTAL" table of a football-data.org standings response.
     */
    static List<TeamWithStanding> fromFootballData(JsonNode response) {
        List<TeamWithStanding> table = new ArrayList<>();
        if (response == null) {
            return table;
        }
        for (JsonNode group : response.path("standings")) {
            if (!"TOTAL".equals(group.path("type").asText())) {
                continue;
            }
            for (JsonNode e : group.path("table")) {
                JsonNode team = e.path("team");
                table.add(new TeamWithStanding(
                        e.path("position").asInt(),
                        team.path("id").asInt(),
                        team.path("shortName").asText(team.path("name").asText()),
                        team.path("tla").asText(),
                        e.path("playedGames").asInt(),
                        e.path("won").asInt(),
                        e.path("draw").asInt(),
                        e.path("lost").asInt(),
                        e.path("goalsFor").asInt(),
                        e.path("goalsAgainst").asInt(),
                        e.path("goalDifference").asInt(),
                        e.path("points").asInt(),
                        e.path("form").isTextual() ? e.path("form").asText() : null,
                        team.path("crest").isTextual() ? team.path("crest").asText() : null
                ));
            }
            break;
        }
        return table;
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
                    goalsFor, goalsAgainst, goalDifference(), points(), null, null);
        }
    }
}
