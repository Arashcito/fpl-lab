package com.arash.fpllab.prediction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the best starting XI and captain, and searches for the best 0, 1 or 2 transfers.
 *
 * Squad value over the horizon:
 *   value = sum over gameweeks of ( best XI points + captain points (counted twice) + 0.1 x bench points )
 * A transfer plan is scored as:
 *   net gain = value(new squad) - value(current squad) - 4 x (transfers beyond free transfers)
 */
public final class SquadOptimizer {

    private SquadOptimizer() {}

    public static final double BENCH_WEIGHT = 0.1;
    public static final double HIT_COST = 4;
    /** Don't spend a free transfer unless it gains at least this much (otherwise roll it). */
    public static final double ROLL_THRESHOLD = 1.0;
    public static final int MAX_PER_CLUB = 3;
    public static final int MAX_FREE_TRANSFERS = 5;
    /** Candidates per position considered for transfers in. */
    static final int POOL_PER_POSITION = 40;
    static final int DOUBLE_POOL_PER_POSITION = 15;

    // formation limits for outfield players: DEF 3-5, MID 2-5, FWD 1-3
    private static final int[] MIN_PLAY = {0, 1, 3, 2, 1};
    private static final int[] MAX_PLAY = {0, 1, 5, 5, 3};

    /** A player in a squad: price in tenths of a million (selling price for owned players). */
    public record Slot(int playerId, int positionId, int teamId, int price, double[] points) {

        double total() {
            double t = 0;
            for (double p : points) t += p;
            return t;
        }
    }

    public record Lineup(List<Slot> starters, List<Slot> bench, Slot captain, Slot viceCaptain, double points) {}

    public record Transfer(Slot out, Slot in) {}

    public record Plan(List<Transfer> transfers, double gain, int hits, double netGain) {}

    public record Result(double currentValue, Plan recommended, boolean rollTransfer, Plan bestSingle,
                         Plan bestDouble, List<Plan> alternatives) {}

    /** Best XI for one gameweek: 1 keeper, then the strongest legal outfield ten. */
    public static Lineup bestLineup(List<Slot> squad, int gw) {
        Map<Integer, List<Slot>> byPos = new HashMap<>();
        for (Slot s : squad) {
            byPos.computeIfAbsent(s.positionId(), k -> new ArrayList<>()).add(s);
        }
        Comparator<Slot> byPoints = Comparator.comparingDouble((Slot s) -> s.points()[gw]).reversed();
        byPos.values().forEach(l -> l.sort(byPoints));

        List<Slot> starters = new ArrayList<>();
        List<Slot> rest = new ArrayList<>();
        int[] used = new int[5];
        for (int pos = 1; pos <= 4; pos++) {
            List<Slot> list = byPos.getOrDefault(pos, List.of());
            for (int i = 0; i < list.size(); i++) {
                if (i < MIN_PLAY[pos]) {
                    starters.add(list.get(i));
                    used[pos]++;
                } else if (pos != 1) {
                    rest.add(list.get(i));
                }
            }
        }
        rest.sort(byPoints);
        List<Slot> bench = new ArrayList<>();
        for (Slot s : rest) {
            if (starters.size() < 11 && used[s.positionId()] < MAX_PLAY[s.positionId()]) {
                starters.add(s);
                used[s.positionId()]++;
            } else {
                bench.add(s);
            }
        }
        // the backup keeper always sits on the bench
        List<Slot> keepers = byPos.getOrDefault(1, List.of());
        for (int i = MIN_PLAY[1]; i < keepers.size(); i++) {
            bench.add(keepers.get(i));
        }

        List<Slot> ranked = new ArrayList<>(starters);
        ranked.sort(byPoints);
        Slot captain = ranked.isEmpty() ? null : ranked.get(0);
        Slot vice = ranked.size() < 2 ? null : ranked.get(1);
        double points = 0;
        for (Slot s : starters) points += s.points()[gw];
        if (captain != null) points += captain.points()[gw];
        return new Lineup(starters, bench, captain, vice, points);
    }

    public static double squadValue(List<Slot> squad, int horizon) {
        double value = 0;
        for (int gw = 0; gw < horizon; gw++) {
            Lineup lineup = bestLineup(squad, gw);
            value += lineup.points();
            for (Slot s : lineup.bench()) value += BENCH_WEIGHT * s.points()[gw];
        }
        return value;
    }

    /**
     * @param squad         the current 15 (prices = selling prices)
     * @param pool          every player that could be bought (prices = current prices)
     * @param bank          money in the bank, tenths of a million
     * @param freeTransfers free transfers available
     */
    public static Result optimise(List<Slot> squad, List<Slot> pool, int bank, int freeTransfers, int horizon) {
        double current = squadValue(squad, horizon);
        Set<Integer> owned = new HashSet<>();
        for (Slot s : squad) owned.add(s.playerId());

        Map<Integer, List<Slot>> candidates = new HashMap<>();
        for (Slot s : pool) {
            if (!owned.contains(s.playerId())) {
                candidates.computeIfAbsent(s.positionId(), k -> new ArrayList<>()).add(s);
            }
        }
        candidates.values().forEach(l -> {
            l.sort(Comparator.comparingDouble(Slot::total).reversed());
            if (l.size() > POOL_PER_POSITION) l.subList(POOL_PER_POSITION, l.size()).clear();
        });

        // ---- one transfer: every squad player x every candidate at the same position ----
        List<Plan> singles = new ArrayList<>();
        for (Slot out : squad) {
            for (Slot in : candidates.getOrDefault(out.positionId(), List.of())) {
                if (in.price() > bank + out.price()) continue;
                List<Slot> next = swap(squad, List.of(new Transfer(out, in)));
                if (!clubLimitOk(next)) continue;
                singles.add(plan(List.of(new Transfer(out, in)), squadValue(next, horizon) - current, freeTransfers));
            }
        }
        singles.sort(Comparator.comparingDouble(Plan::netGain).reversed());

        // ---- two transfers: every pair of squad players x the top candidates for each position ----
        Plan bestDouble = null;
        for (int i = 0; i < squad.size(); i++) {
            for (int j = i + 1; j < squad.size(); j++) {
                Slot out1 = squad.get(i), out2 = squad.get(j);
                List<Slot> ins1 = top(candidates.getOrDefault(out1.positionId(), List.of()));
                List<Slot> ins2 = top(candidates.getOrDefault(out2.positionId(), List.of()));
                int budget = bank + out1.price() + out2.price();
                for (Slot in1 : ins1) {
                    for (Slot in2 : ins2) {
                        if (in1.playerId() == in2.playerId() || in1.price() + in2.price() > budget) continue;
                        var transfers = List.of(new Transfer(out1, in1), new Transfer(out2, in2));
                        List<Slot> next = swap(squad, transfers);
                        if (!clubLimitOk(next)) continue;
                        Plan p = plan(transfers, squadValue(next, horizon) - current, freeTransfers);
                        if (bestDouble == null || p.netGain() > bestDouble.netGain()) bestDouble = p;
                    }
                }
            }
        }

        Plan bestSingle = singles.isEmpty() ? null : singles.get(0);
        Plan none = new Plan(List.of(), 0, 0, 0);
        Plan recommended = none;
        if (bestSingle != null && bestSingle.netGain() > recommended.netGain()) recommended = bestSingle;
        if (bestDouble != null && bestDouble.netGain() > recommended.netGain()) recommended = bestDouble;

        // rolling keeps flexibility; only worth giving up when the gain is real (no point rolling past the cap)
        double threshold = freeTransfers >= MAX_FREE_TRANSFERS ? 0 : ROLL_THRESHOLD;
        boolean roll = recommended.transfers().isEmpty() || recommended.netGain() < threshold;
        if (roll) recommended = none;

        // alternatives: best single transfer per incoming player
        List<Plan> alternatives = new ArrayList<>();
        Set<Integer> seenIn = new HashSet<>();
        for (Plan p : singles) {
            if (alternatives.size() >= 5) break;
            if (seenIn.add(p.transfers().get(0).in().playerId())) alternatives.add(p);
        }
        return new Result(current, recommended, roll, bestSingle, bestDouble, alternatives);
    }

    private static List<Slot> top(List<Slot> list) {
        return list.size() > DOUBLE_POOL_PER_POSITION ? list.subList(0, DOUBLE_POOL_PER_POSITION) : list;
    }

    private static Plan plan(List<Transfer> transfers, double gain, int freeTransfers) {
        int hits = Math.max(0, transfers.size() - freeTransfers);
        return new Plan(transfers, gain, hits, gain - HIT_COST * hits);
    }

    static List<Slot> swap(List<Slot> squad, List<Transfer> transfers) {
        List<Slot> next = new ArrayList<>(squad);
        for (Transfer t : transfers) {
            next.remove(t.out());
            next.add(t.in());
        }
        return next;
    }

    static boolean clubLimitOk(List<Slot> squad) {
        Map<Integer, Integer> perClub = new HashMap<>();
        for (Slot s : squad) {
            if (perClub.merge(s.teamId(), 1, Integer::sum) > MAX_PER_CLUB) return false;
        }
        return true;
    }
}
