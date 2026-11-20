package com.arash.fpllab.prediction;

import java.util.HashMap;
import java.util.Map;

/**
 * Attack and defence strength of each team, relative to the league average (1.0 = average).
 *
 *   attackRate  = 0.7 x xG per game + 0.3 x goals per game
 *   defenceRate = 0.7 x xG conceded per game + 0.3 x goals conceded per game
 *   both shrunk toward the league average with 4 "average" games, then
 *   attack  = attackRate  / leagueAverage
 *   defence = defenceRate / leagueAverage   (above 1.0 = concedes more than average)
 */
public record TeamStrength(double attack, double defence) {

    /** Season totals for one team. */
    public record TeamTotals(int games, double goalsFor, double goalsAgainst,
                             double xgFor, double xgAgainst) {}

    public record Table(Map<Integer, TeamStrength> teams, double leagueAverageGoals) {

        public TeamStrength of(int teamId) {
            return teams.getOrDefault(teamId, new TeamStrength(1.0, 1.0));
        }

        /** Expected goals for `team` against `opponent`. */
        public double expectedGoals(int team, int opponent, boolean home) {
            return leagueAverageGoals * of(team).attack() * of(opponent).defence()
                    * (home ? PointsModel.HOME_FACTOR : PointsModel.AWAY_FACTOR);
        }

        public PointsModel.FixtureContext context(int team, int opponent, boolean home) {
            double venue = home ? PointsModel.HOME_FACTOR : PointsModel.AWAY_FACTOR;
            double opponentVenue = home ? PointsModel.AWAY_FACTOR : PointsModel.HOME_FACTOR;
            return new PointsModel.FixtureContext(
                    of(opponent).defence() * venue,
                    expectedGoals(opponent, team, !home),
                    of(opponent).attack() * opponentVenue);
        }
    }

    public static Table compute(Map<Integer, TeamTotals> totals) {
        Map<Integer, double[]> raw = new HashMap<>();
        double sumAttack = 0;
        int counted = 0;
        for (var e : totals.entrySet()) {
            TeamTotals t = e.getValue();
            if (t.games() == 0) {
                continue;
            }
            double attack = blend(t.xgFor(), t.goalsFor()) / t.games();
            double defence = blend(t.xgAgainst(), t.goalsAgainst()) / t.games();
            raw.put(e.getKey(), new double[]{attack, defence, t.games()});
            sumAttack += attack;
            counted++;
        }
        // every goal scored is a goal conceded, so one average describes both sides
        double leagueAverage = counted == 0 ? 1.35 : sumAttack / counted;

        Map<Integer, TeamStrength> result = new HashMap<>();
        for (Integer teamId : totals.keySet()) {
            double[] r = raw.get(teamId);
            if (r == null) {
                result.put(teamId, new TeamStrength(1.0, 1.0));
                continue;
            }
            double attack = PointsModel.shrunkTeamRate(r[0], r[2], leagueAverage) / leagueAverage;
            double defence = PointsModel.shrunkTeamRate(r[1], r[2], leagueAverage) / leagueAverage;
            result.put(teamId, new TeamStrength(attack, defence));
        }
        return new Table(result, leagueAverage);
    }

    private static double blend(double expected, double actual) {
        return PointsModel.XG_WEIGHT * expected + (1 - PointsModel.XG_WEIGHT) * actual;
    }
}
