package com.arash.fpllab.service;

import com.arash.fpllab.model.TeamWithStanding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String TEAMS = """
            [
              {"id": 1, "name": "Arsenal",   "short_name": "ARS"},
              {"id": 2, "name": "Chelsea",   "short_name": "CHE"},
              {"id": 3, "name": "Liverpool", "short_name": "LIV"}
            ]
            """;

    @Test
    void buildsTableFromFinishedFixtures() throws Exception {
        String fixtures = """
                [
                  {"team_h": 1, "team_a": 2, "team_h_score": 2, "team_a_score": 0, "finished": true,  "finished_provisional": true},
                  {"team_h": 3, "team_a": 1, "team_h_score": 1, "team_a_score": 1, "finished": false, "finished_provisional": true},
                  {"team_h": 2, "team_a": 3, "team_h_score": 3, "team_a_score": 1, "finished": true,  "finished_provisional": true},
                  {"team_h": 1, "team_a": 3, "team_h_score": 1, "team_a_score": 0, "finished": false, "finished_provisional": false},
                  {"team_h": 2, "team_a": 1, "team_h_score": null, "team_a_score": null, "finished": false, "finished_provisional": false}
                ]
                """;

        List<TeamWithStanding> table = StandingsService.calculate(json(TEAMS), json(fixtures));

        // Arsenal: W1 D1 -> 4 pts, GF 3 GA 1
        TeamWithStanding first = table.get(0);
        assertEquals("Arsenal", first.name());
        assertEquals(1, first.position());
        assertEquals(2, first.played());
        assertEquals(4, first.points());
        assertEquals(3, first.goalsFor());
        assertEquals(1, first.goalsAgainst());
        assertEquals(2, first.goalDifference());

        // Chelsea: W1 L1 -> 3 pts, GD 0
        assertEquals("Chelsea", table.get(1).name());
        assertEquals(3, table.get(1).points());

        // Liverpool: D1 L1 -> 1 pt; in-progress match is ignored
        assertEquals("Liverpool", table.get(2).name());
        assertEquals(1, table.get(2).points());
        assertEquals(2, table.get(2).played());
        assertEquals(3, table.get(2).position());
    }

    @Test
    void tiesBrokenByGoalDifferenceThenGoalsScored() throws Exception {
        String fixtures = """
                [
                  {"team_h": 1, "team_a": 3, "team_h_score": 1, "team_a_score": 0, "finished": true},
                  {"team_h": 2, "team_a": 3, "team_h_score": 3, "team_a_score": 2, "finished": true}
                ]
                """;

        List<TeamWithStanding> table = StandingsService.calculate(json(TEAMS), json(fixtures));

        // Both on 3 pts and GD +1; Chelsea scored more
        assertEquals("Chelsea", table.get(0).name());
        assertEquals("Arsenal", table.get(1).name());
    }

    @Test
    void teamsWithNoMatchesStillAppear() throws Exception {
        List<TeamWithStanding> table = StandingsService.calculate(json(TEAMS), json("[]"));

        assertEquals(3, table.size());
        assertEquals(0, table.get(0).points());
    }

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }
}
