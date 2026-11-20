package com.arash.fpllab.prediction;

/**
 * Expected FPL points for one player in one fixture.
 *
 * Every formula is documented in docs/prediction-model.md. In short:
 *
 *   xPts = appearance + goals + assists + clean sheet + goals conceded
 *        + saves + bonus + defensive contribution + cards
 *
 * where each part is (chance it happens) x (FPL points for it), using
 * the player's per-90 rates, expected minutes and the opponent's strength.
 */
public final class PointsModel {

    private PointsModel() {}

    // ---- FPL scoring (game_config.scoring), indexed by position id: 1 GKP, 2 DEF, 3 MID, 4 FWD ----
    static final double[] GOAL_POINTS = {0, 10, 6, 5, 4};
    static final double[] CLEAN_SHEET_POINTS = {0, 4, 4, 1, 0};
    static final double ASSIST_POINTS = 3;
    static final double PLAY_POINTS = 1;        // 1-59 minutes
    static final double SIXTY_MIN_BONUS = 1;    // +1 more for 60+ minutes
    static final double YELLOW_CARD_POINTS = -1;
    static final double GOALS_CONCEDED_POINTS = -1; // per 2 conceded, GKP/DEF
    static final double SAVE_POINTS = 1;            // per 3 saves, GKP

    // ---- model settings ----
    /** Home teams score ~10% more than average, away teams ~10% less. */
    public static final double HOME_FACTOR = 1.10;
    public static final double AWAY_FACTOR = 0.90;
    /** Shrinkage: every player's per-90 rates start from 270 minutes of "average player at his position". */
    public static final double PRIOR_MINUTES = 270;
    /** Shrinkage for teams: 4 games of "average team". */
    public static final double PRIOR_TEAM_GAMES = 4;
    /** Season rates vs. last-6-matches rates when blending attacking form. */
    public static final double SEASON_WEIGHT = 0.6;
    /** Expected-goals vs. actual-goals weight in team strength. */
    public static final double XG_WEIGHT = 0.7;
    /** Minutes model looks at the team's last 6 matches, newer ones weighted more (x0.85 per match back). */
    public static final int MINUTES_WINDOW = 6;
    public static final double MINUTES_DECAY = 0.85;

    /** Per-90 rates that describe what a player does when on the pitch. */
    public record Rates(double xg90, double xa90, double bonus90, double saves90,
                        double yellow90, double defensiveContribution90) {}

    /** How much the player is expected to play in the next match. */
    public record Minutes(double playProbability, double sixtyProbability, double expectedMinutes) {

        public Minutes scaled(double availability) {
            return new Minutes(playProbability * availability, sixtyProbability * availability,
                    expectedMinutes * availability);
        }
    }

    /**
     * One fixture from the player's team's point of view.
     *
     * @param attackMultiplier how much easier than average it is to score here:
     *                         opponent defence weakness x home/away factor
     * @param goalsAgainst     expected goals the player's team concedes (lambda)
     * @param shotsMultiplier  how much more than average the opponent attacks (for keeper saves)
     */
    public record FixtureContext(double attackMultiplier, double goalsAgainst, double shotsMultiplier) {}

    /** Expected points split by where they come from. */
    public record Breakdown(double appearance, double goals, double assists, double cleanSheet,
                            double goalsConceded, double saves, double bonus,
                            double defensiveContribution, double cards) {

        public static final Breakdown ZERO = new Breakdown(0, 0, 0, 0, 0, 0, 0, 0, 0);

        public double total() {
            return appearance + goals + assists + cleanSheet + goalsConceded + saves + bonus
                    + defensiveContribution + cards;
        }

        public Breakdown plus(Breakdown o) {
            return new Breakdown(appearance + o.appearance, goals + o.goals, assists + o.assists,
                    cleanSheet + o.cleanSheet, goalsConceded + o.goalsConceded, saves + o.saves,
                    bonus + o.bonus, defensiveContribution + o.defensiveContribution, cards + o.cards);
        }
    }

    /**
     * Expected points for one fixture.
     */
    public static Breakdown expectedPoints(int positionId, Rates r, Minutes m, FixtureContext f) {
        double share = m.expectedMinutes() / 90.0;   // fraction of a full match on the pitch
        boolean keeperOrDefender = positionId == 1 || positionId == 2;

        double appearance = PLAY_POINTS * m.playProbability() + SIXTY_MIN_BONUS * m.sixtyProbability();
        double goals = r.xg90() * share * f.attackMultiplier() * GOAL_POINTS[positionId];
        double assists = r.xa90() * share * f.attackMultiplier() * ASSIST_POINTS;

        double cleanSheetChance = Math.exp(-f.goalsAgainst());           // Poisson P(0 goals)
        double cleanSheet = CLEAN_SHEET_POINTS[positionId] * m.sixtyProbability() * cleanSheetChance;

        double goalsConceded = keeperOrDefender
                ? GOALS_CONCEDED_POINTS * m.sixtyProbability() * expectedFloorDiv(f.goalsAgainst(), 2)
                : 0;

        double saves = positionId == 1
                ? SAVE_POINTS * expectedFloorDiv(r.saves90() * share * f.shotsMultiplier(), 3)
                : 0;

        double bonus = r.bonus90() * share;
        double defensive = positionId == 1 ? 0 : r.defensiveContribution90() * share;
        double cards = YELLOW_CARD_POINTS * r.yellow90() * share;

        return new Breakdown(appearance, goals, assists, cleanSheet, goalsConceded, saves, bonus, defensive, cards);
    }

    /**
     * E[floor(N / d)] for N ~ Poisson(lambda).
     * Used for "-1 per 2 goals conceded" and "+1 per 3 saves".
     */
    public static double expectedFloorDiv(double lambda, int d) {
        if (lambda <= 0) {
            return 0;
        }
        double p = Math.exp(-lambda);   // P(N = 0)
        double expectation = 0;
        for (int n = 1; n <= 40; n++) {
            p *= lambda / n;             // P(N = n)
            expectation += (n / d) * p;
        }
        return expectation;
    }

    /**
     * Per-90 rate with shrinkage toward the position average:
     *   rate90 = (total + prior90 x PRIOR_MINUTES / 90) / (minutes + PRIOR_MINUTES) x 90
     * A player with few minutes stays close to the average; a regular starter is mostly his own numbers.
     */
    public static double shrunkPer90(double total, double minutes, double prior90) {
        return (total + prior90 * PRIOR_MINUTES / 90.0) / (minutes + PRIOR_MINUTES) * 90.0;
    }

    /**
     * Team rate per game shrunk toward the league average:
     *   (rate x games + leagueAverage x PRIOR_TEAM_GAMES) / (games + PRIOR_TEAM_GAMES)
     */
    public static double shrunkTeamRate(double ratePerGame, double games, double leagueAverage) {
        return (ratePerGame * games + leagueAverage * PRIOR_TEAM_GAMES) / (games + PRIOR_TEAM_GAMES);
    }

    /**
     * Chance the player is available, from FPL's injury/suspension flag.
     * chanceOfPlaying is FPL's "75% chance of playing" (null when there is no flag).
     *
     * @param gameweeksAhead 0 = next gameweek, 1 = the one after, ...
     */
    public static double availability(String status, Integer chanceOfPlaying, int gameweeksAhead) {
        double now;
        if (chanceOfPlaying != null) {
            now = chanceOfPlaying / 100.0;
        } else {
            now = switch (status == null ? "a" : status) {
                case "a" -> 1.0;
                case "d" -> 0.75;
                default -> 0.0;   // injured, suspended, unavailable, left the club
            };
        }
        if (gameweeksAhead == 0 || now >= 1.0) {
            return now;
        }
        // Doubtful players usually recover; injured / suspended / departed players keep their value.
        if ("d".equals(status)) {
            return (now + 1.0) / 2.0;
        }
        return now;
    }
}
