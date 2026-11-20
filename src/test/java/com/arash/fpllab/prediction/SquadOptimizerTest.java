package com.arash.fpllab.prediction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadOptimizerTest {

    private static int nextId = 1;

    private static SquadOptimizer.Slot slot(int pos, int team, int price, double points) {
        return new SquadOptimizer.Slot(nextId++, pos, team, price, new double[]{points});
    }

    /** 2 GKP, 5 DEF, 5 MID, 3 FWD, all from different clubs. */
    private static List<SquadOptimizer.Slot> squad() {
        List<SquadOptimizer.Slot> s = new ArrayList<>();
        int team = 1;
        s.add(slot(1, team++, 45, 4.0));
        s.add(slot(1, team++, 40, 1.0));
        for (int i = 0; i < 5; i++) s.add(slot(2, team++, 45, 3.0 + i * 0.1));
        for (int i = 0; i < 5; i++) s.add(slot(3, team++, 60, 4.0 + i * 0.1));
        for (int i = 0; i < 3; i++) s.add(slot(4, team++, 70, 4.5 + i * 0.1));
        return s;
    }

    @Test
    void lineupIsLegalAndCaptainIsBestPlayer() {
        var squad = squad();
        var lineup = SquadOptimizer.bestLineup(squad, 0);

        assertEquals(11, lineup.starters().size());
        assertEquals(4, lineup.bench().size());
        long keepers = lineup.starters().stream().filter(s -> s.positionId() == 1).count();
        long defenders = lineup.starters().stream().filter(s -> s.positionId() == 2).count();
        long forwards = lineup.starters().stream().filter(s -> s.positionId() == 4).count();
        assertEquals(1, keepers);
        assertTrue(defenders >= 3 && defenders <= 5);
        assertTrue(forwards >= 1 && forwards <= 3);
        assertEquals(4.7, lineup.captain().points()[0], 1e-9);   // best forward
    }

    @Test
    void findsFreeUpgradeWithinBudget() {
        var squad = squad();
        var weakDefender = squad.get(2);   // 3.0 pts, price 45
        var greatDefender = slot(2, 99, 50, 9.0);
        var tooExpensive = slot(2, 98, 100, 12.0);

        var result = SquadOptimizer.optimise(squad, List.of(greatDefender, tooExpensive), 5, 1, 1);

        assertFalse(result.rollTransfer());
        var t = result.recommended().transfers().get(0);
        assertEquals(weakDefender.playerId(), t.out().playerId());
        assertEquals(greatDefender.playerId(), t.in().playerId());
        assertEquals(0, result.recommended().hits());
        assertTrue(result.recommended().netGain() > 5);
    }

    @Test
    void respectsThreePlayersPerClub() {
        var squad = squad();
        // put three players from club 50 in the squad
        squad.set(2, new SquadOptimizer.Slot(900, 2, 50, 45, new double[]{3.0}));
        squad.set(3, new SquadOptimizer.Slot(901, 2, 50, 45, new double[]{3.1}));
        squad.set(7, new SquadOptimizer.Slot(902, 3, 50, 60, new double[]{4.0}));
        var fourthFromClub = slot(4, 50, 70, 15.0);

        var result = SquadOptimizer.optimise(squad, List.of(fourthFromClub), 100, 1, 1);

        // only legal if one of the club-50 players goes out; a forward can only replace a forward
        assertTrue(result.bestSingle() == null);
    }

    @Test
    void hitsCostFourPoints() {
        var squad = squad();
        var upgrade = slot(3, 97, 60, 6.0);   // replaces a 4.0 midfielder: +2.0 gross

        var result = SquadOptimizer.optimise(squad, List.of(upgrade), 0, 0, 1);

        assertEquals(1, result.bestSingle().hits());
        assertEquals(result.bestSingle().gain() - 4, result.bestSingle().netGain(), 1e-9);
        assertTrue(result.rollTransfer());   // -4 hit for +2 isn't worth it
    }

    @Test
    void rollsWhenGainIsBelowThreshold() {
        var squad = squad();
        var tinyUpgrade = slot(3, 96, 60, 4.5);   // +0.5 over the worst starting midfielder

        var result = SquadOptimizer.optimise(squad, List.of(tinyUpgrade), 0, 1, 1);

        assertTrue(result.rollTransfer());
        assertTrue(result.recommended().transfers().isEmpty());
    }
}
