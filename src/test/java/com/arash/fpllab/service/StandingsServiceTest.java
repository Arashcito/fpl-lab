package com.arash.fpllab.service;

import com.arash.fpllab.model.TeamWithStanding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void mapsFootballDataTotalTable() throws Exception {
        String response = """
                {
                  "competition": {"code": "PL"},
                  "standings": [
                    {"type": "HOME", "table": [
                      {"position": 1, "team": {"id": 99, "name": "Wrong FC"}, "points": 99}
                    ]},
                    {"type": "TOTAL", "table": [
                      {"position": 1, "team": {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "tla": "ARS",
                                               "crest": "https://crests.football-data.org/57.png"},
                       "playedGames": 12, "form": "W,W,D,W,L", "won": 8, "draw": 3, "lost": 1,
                       "points": 27, "goalsFor": 22, "goalsAgainst": 7, "goalDifference": 15},
                      {"position": 2, "team": {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "tla": "MCI"},
                       "playedGames": 12, "form": null, "won": 8, "draw": 1, "lost": 3,
                       "points": 25, "goalsFor": 26, "goalsAgainst": 10, "goalDifference": 16}
                    ]}
                  ]
                }
                """;

        List<TeamWithStanding> table = StandingsService.fromFootballData(json(response));

        assertEquals(2, table.size());
        TeamWithStanding first = table.get(0);
        assertEquals(1, first.position());
        assertEquals("Arsenal", first.name());
        assertEquals("ARS", first.shortName());
        assertEquals(12, first.played());
        assertEquals(3, first.drawn());
        assertEquals(27, first.points());
        assertEquals(15, first.goalDifference());
        assertEquals("W,W,D,W,L", first.form());
        assertEquals("https://crests.football-data.org/57.png", first.crest());

        assertEquals("Man City", table.get(1).name());
        assertNull(table.get(1).form());
        assertNull(table.get(1).crest());
    }

    @Test
    void emptyFootballDataResponseGivesEmptyTable() throws Exception {
        assertTrue(StandingsService.fromFootballData(json("{}")).isEmpty());
        assertTrue(StandingsService.fromFootballData(null).isEmpty());
    }
}
