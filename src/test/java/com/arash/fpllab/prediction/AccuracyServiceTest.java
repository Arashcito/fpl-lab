package com.arash.fpllab.prediction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccuracyServiceTest {

    private static final double EPS = 1e-9;

    private static AccuracyService.Pair p(double predicted, double actual) {
        return new AccuracyService.Pair(0, predicted, actual);
    }

    @Test
    void errorAndBias() {
        var pairs = List.of(p(4, 2), p(2, 5), p(3, 3));
        assertEquals((2 + 3 + 0) / 3.0, AccuracyService.meanAbsoluteError(pairs), EPS);
        assertEquals((2 - 3 + 0) / 3.0, AccuracyService.bias(pairs), EPS);
    }

    @Test
    void rankCorrelationIsOneForPerfectOrderAndMinusOneForReversed() {
        assertEquals(1.0, AccuracyService.rankCorrelation(List.of(p(1, 2), p(2, 6), p(3, 9))), EPS);
        assertEquals(-1.0, AccuracyService.rankCorrelation(List.of(p(1, 9), p(2, 6), p(3, 2))), EPS);
    }

    @Test
    void tiedValuesShareTheirAverageRank() {
        assertArrayEquals(new double[]{1, 2.5, 2.5, 4}, AccuracyService.ranks(new double[]{1, 5, 5, 9}), EPS);
    }

    @Test
    void topPicksAverageUsesHighestPredictions() {
        var pairs = List.of(p(9, 2), p(8, 10), p(1, 15));
        assertEquals(6.0, AccuracyService.topPicksAverage(pairs, 2), EPS);   // (2 + 10) / 2
    }

    @Test
    void overallOnlyCountsFinishedGameweeks() {
        var labGood = new AccuracyService.Score("fpl-lab", 300, 1.8, 0.1, 0.4, 6.0, 1, "A", 7, 12);
        var officialWorse = new AccuracyService.Score("fpl-official", 300, 2.2, 0.5, 0.3, 4.0, 2, "B", 8, 2);
        var finished = new AccuracyService.GameweekSummary(10, "finished", null, labGood, null, officialWorse, 1, "A", 12);
        var inProgress = new AccuracyService.GameweekSummary(11, "in progress", null, officialWorse, null, labGood, 1, "A", 5);

        var overall = AccuracyService.overall(List.of(finished, inProgress));

        assertEquals(1, overall.gameweeksScored());
        assertEquals(1.8, overall.fplLabError(), EPS);
        assertEquals(2.2, overall.fplOfficialError(), EPS);
        assertEquals(1, overall.fplLabWins());
        assertEquals(0, overall.fplOfficialWins());
        assertEquals(0, overall.mlGameweeksScored());
        assertNull(overall.fplLabMlError());
    }

    @Test
    void mlModelIsScoredOnItsOwnGameweeksAndCanWin() {
        var lab = new AccuracyService.Score("fpl-lab", 300, 2.0, 0, 0.3, 5.0, 1, "A", 6, 2);
        var official = new AccuracyService.Score("fpl-official", 300, 2.2, 0, 0.3, 4.0, 2, "B", 7, 3);
        var ml = new AccuracyService.Score("fpl-lab-ml", 300, 1.9, 0, 0.35, 6.0, 3, "C", 5, 9);
        var beforeMl = new AccuracyService.GameweekSummary(9, "finished", null, lab, null, official, 1, "A", 10);
        var withMl = new AccuracyService.GameweekSummary(10, "finished", null, lab, ml, official, 1, "A", 10);

        var overall = AccuracyService.overall(List.of(withMl, beforeMl));

        assertEquals(2, overall.gameweeksScored());
        assertEquals(1, overall.mlGameweeksScored());
        assertEquals(1.9, overall.fplLabMlError(), EPS);
        assertEquals(6.0, overall.fplLabMlTopPicks(), EPS);
        assertEquals(1, overall.fplLabMlWins());   // GW10: ML has the lowest error
        assertEquals(1, overall.fplLabWins());     // GW9: no ML yet, the formula beats FPL
        assertEquals(0, overall.fplOfficialWins());
    }

    @Test
    void overallIsEmptyBeforeAnyGameweekFinishes() {
        var overall = AccuracyService.overall(List.of());
        assertEquals(0, overall.gameweeksScored());
        assertNull(overall.fplLabError());
    }
}
