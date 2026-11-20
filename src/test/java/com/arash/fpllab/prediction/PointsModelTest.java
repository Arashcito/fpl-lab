package com.arash.fpllab.prediction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointsModelTest {

    private static final double EPS = 1e-6;

    @Test
    void expectedFloorDivMatchesDirectPoissonSum() {
        // lambda = 1.5, E[floor(N/2)] = sum_n floor(n/2) e^-1.5 1.5^n / n!
        double lambda = 1.5, expected = 0, p = Math.exp(-lambda);
        for (int n = 1; n < 30; n++) {
            p *= lambda / n;
            expected += (n / 2) * p;
        }
        assertEquals(expected, PointsModel.expectedFloorDiv(lambda, 2), EPS);
        assertEquals(0, PointsModel.expectedFloorDiv(0, 3), EPS);
    }

    @Test
    void shrinkagePullsSmallSamplesToThePrior() {
        // no minutes -> exactly the prior
        assertEquals(0.3, PointsModel.shrunkPer90(0, 0, 0.3), EPS);
        // 900 minutes, 9 xG (0.9 per 90), prior 0.3: (9 + 0.3*3) / 1170 * 90 = 0.7615...
        assertEquals((9 + 0.9) / 1170.0 * 90, PointsModel.shrunkPer90(9, 900, 0.3), EPS);
    }

    @Test
    void availabilityUsesFplFlags() {
        assertEquals(1.0, PointsModel.availability("a", null, 0), EPS);
        assertEquals(0.75, PointsModel.availability("d", 75, 0), EPS);
        assertEquals(0.875, PointsModel.availability("d", 75, 1), EPS);   // doubtful players recover
        assertEquals(0.0, PointsModel.availability("i", 0, 2), EPS);       // injured stays out
        assertEquals(0.0, PointsModel.availability("u", null, 0), EPS);
    }

    @Test
    void forwardPointsAddUpByHand() {
        var rates = new PointsModel.Rates(0.5, 0.2, 0.6, 0, 0.1, 0);
        var minutes = new PointsModel.Minutes(1.0, 1.0, 90);
        var fixture = new PointsModel.FixtureContext(1.2, 1.0, 1.0);

        var b = PointsModel.expectedPoints(4, rates, minutes, fixture);

        assertEquals(2.0, b.appearance(), EPS);
        assertEquals(0.5 * 1.2 * 4, b.goals(), EPS);      // xG90 x multiplier x 4 pts
        assertEquals(0.2 * 1.2 * 3, b.assists(), EPS);    // xA90 x multiplier x 3 pts
        assertEquals(0, b.cleanSheet(), EPS);             // forwards get no clean sheet points
        assertEquals(0, b.goalsConceded(), EPS);
        assertEquals(0.6, b.bonus(), EPS);
        assertEquals(-0.1, b.cards(), EPS);
        assertEquals(2.0 + 2.4 + 0.72 + 0.6 - 0.1, b.total(), EPS);
    }

    @Test
    void defenderCleanSheetAndGoalsConceded() {
        var rates = new PointsModel.Rates(0, 0, 0, 0, 0, 0.5);
        var minutes = new PointsModel.Minutes(1.0, 0.8, 80);
        var fixture = new PointsModel.FixtureContext(1.0, 1.2, 1.0);

        var b = PointsModel.expectedPoints(2, rates, minutes, fixture);

        assertEquals(1.0 + 0.8, b.appearance(), EPS);
        assertEquals(4 * 0.8 * Math.exp(-1.2), b.cleanSheet(), EPS);
        assertEquals(-0.8 * PointsModel.expectedFloorDiv(1.2, 2), b.goalsConceded(), EPS);
        assertEquals(0.5 * 80 / 90.0, b.defensiveContribution(), EPS);
    }

    @Test
    void playerWhoDoesNotPlayScoresNothing() {
        var rates = new PointsModel.Rates(0.8, 0.3, 1.0, 3, 0.2, 1.0);
        var b = PointsModel.expectedPoints(1, rates, new PointsModel.Minutes(0, 0, 0),
                new PointsModel.FixtureContext(1, 1, 1));
        assertEquals(0, b.total(), EPS);
    }

    @Test
    void teamStrengthIsRelativeToLeagueAverage() {
        var totals = new java.util.HashMap<Integer, TeamStrength.TeamTotals>();
        totals.put(1, new TeamStrength.TeamTotals(10, 20, 10, 20, 10));   // strong
        totals.put(2, new TeamStrength.TeamTotals(10, 10, 20, 10, 20));   // weak
        var table = TeamStrength.compute(totals);

        assertEquals(1.5, table.leagueAverageGoals(), EPS);
        assertTrue(table.of(1).attack() > 1 && table.of(1).defence() < 1);
        assertTrue(table.of(2).attack() < 1 && table.of(2).defence() > 1);
        // home side expects more goals than the same matchup away
        assertTrue(table.expectedGoals(1, 2, true) > table.expectedGoals(1, 2, false));
    }
}
