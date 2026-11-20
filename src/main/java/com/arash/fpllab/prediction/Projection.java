package com.arash.fpllab.prediction;

import java.util.List;
import java.util.Map;

/**
 * Projection records returned by the API.
 */
public final class Projection {

    private Projection() {}

    public record FixtureInfo(int opponentId, String opponent, boolean home, Integer difficulty) {}

    public record Gameweek(int event, List<FixtureInfo> fixtures, double points) {}

    /** The numbers the model used for this player, so the result can be explained. */
    public record Inputs(double xg90, double xa90, double bonus90, double defensiveContribution90,
                         double playProbability, double sixtyProbability, double expectedMinutes,
                         int matchesInWindow, int sixtyPlusInWindow, double availability,
                         double cleanSheetChanceNext, double attackMultiplierAvg) {}

    public record Player(int id, String name, int teamId, String team, int positionId, String position,
                         double price, int totalPoints, String status, Integer chanceOfPlaying, String news,
                         double selectedByPercent, Integer penaltiesOrder,
                         List<Gameweek> gameweeks, double total,
                         PointsModel.Breakdown nextBreakdown, Inputs inputs, List<String> reasons) {

        public double pointsIn(int gameweekIndex) {
            return gameweekIndex < gameweeks.size() ? gameweeks.get(gameweekIndex).points() : 0;
        }
    }

    /** Projections for every player over the next few gameweeks. */
    public record Set(int firstEvent, List<Integer> events, Map<Integer, Player> players,
                      Map<Integer, String> teamShortNames) {

        public static Set empty() {
            return new Set(0, List.of(), Map.of(), Map.of());
        }
    }
}
