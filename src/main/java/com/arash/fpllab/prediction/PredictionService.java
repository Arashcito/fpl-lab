package com.arash.fpllab.prediction;

import com.arash.fpllab.entity.FixtureEntity;
import com.arash.fpllab.entity.GameweekEntity;
import com.arash.fpllab.entity.PlayerEntity;
import com.arash.fpllab.entity.PlayerGameweekEntity;
import com.arash.fpllab.entity.TeamEntity;
import com.arash.fpllab.repository.FixtureRepository;
import com.arash.fpllab.repository.GameweekRepository;
import com.arash.fpllab.repository.PlayerGameweekRepository;
import com.arash.fpllab.repository.PlayerRepository;
import com.arash.fpllab.repository.TeamRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Projects expected FPL points for every player over the next few gameweeks,
 * using the data SyncService stores in the database.
 */
@Service
public class PredictionService {

    public static final int MAX_HORIZON = 6;
    private static final String[] POSITIONS = {"", "GKP", "DEF", "MID", "FWD"};
    /** Players need this many minutes to count toward their position's average (the shrinkage prior). */
    private static final int PRIOR_MIN_MINUTES = 450;

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final FixtureRepository fixtureRepository;
    private final GameweekRepository gameweekRepository;
    private final PlayerGameweekRepository playerGameweekRepository;

    public PredictionService(PlayerRepository playerRepository, TeamRepository teamRepository,
                             FixtureRepository fixtureRepository, GameweekRepository gameweekRepository,
                             PlayerGameweekRepository playerGameweekRepository) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.fixtureRepository = fixtureRepository;
        this.gameweekRepository = gameweekRepository;
        this.playerGameweekRepository = playerGameweekRepository;
    }

    @Cacheable(value = "projections", key = "#horizon")
    public Projection.Set project(int horizon) {
        int h = Math.max(1, Math.min(MAX_HORIZON, horizon));
        var next = gameweekRepository.findFirstByNextTrue();
        if (next.isEmpty()) {
            return Projection.Set.empty();   // season over, or data not synced yet
        }
        int firstEvent = next.get().getId();
        int lastEvent = Math.min(38, firstEvent + h - 1);
        List<Integer> events = new ArrayList<>();
        for (int e = firstEvent; e <= lastEvent; e++) {
            events.add(e);
        }

        Map<Integer, String> teamNames = teamRepository.findAll().stream()
                .collect(Collectors.toMap(TeamEntity::getId, TeamEntity::getShortName));
        List<PlayerEntity> players = playerRepository.findAll();
        Map<Integer, PlayerEntity> playersById = players.stream()
                .collect(Collectors.toMap(PlayerEntity::getId, p -> p));
        List<FixtureEntity> fixtures = fixtureRepository.findAll();
        List<PlayerGameweekEntity> history = playerGameweekRepository.findAll();

        // which events each team has played, and how many matches in each (2 in a double gameweek)
        Map<Integer, TreeMap<Integer, Integer>> playedByTeam = new HashMap<>();
        for (FixtureEntity f : fixtures) {
            if (f.hasResult() && f.getEvent() != null) {
                playedByTeam.computeIfAbsent(f.getTeamH(), k -> new TreeMap<>()).merge(f.getEvent(), 1, Integer::sum);
                playedByTeam.computeIfAbsent(f.getTeamA(), k -> new TreeMap<>()).merge(f.getEvent(), 1, Integer::sum);
            }
        }

        Map<Long, PlayerGameweekEntity> historyByKey = new HashMap<>();
        Map<Integer, Double> defensivePointsTotal = new HashMap<>();
        for (PlayerGameweekEntity row : history) {
            historyByKey.put(row.getId(), row);
            defensivePointsTotal.merge(row.getPlayerId(), (double) nz(row.getDefensiveContributionPoints()), Double::sum);
        }

        TeamStrength.Table strength = TeamStrength.compute(
                teamTotals(teamNames.keySet(), fixtures, history, playersById, playedByTeam));
        Map<Integer, PointsModel.Rates> priors = positionPriors(players, defensivePointsTotal);

        // upcoming fixtures per team per event
        Map<Integer, Map<Integer, List<FixtureEntity>>> upcoming = new HashMap<>();
        for (FixtureEntity f : fixtures) {
            if (f.getEvent() == null || f.getEvent() < firstEvent || f.getEvent() > lastEvent) {
                continue;
            }
            upcoming.computeIfAbsent(f.getTeamH(), k -> new HashMap<>())
                    .computeIfAbsent(f.getEvent(), k -> new ArrayList<>()).add(f);
            upcoming.computeIfAbsent(f.getTeamA(), k -> new HashMap<>())
                    .computeIfAbsent(f.getEvent(), k -> new ArrayList<>()).add(f);
        }

        Map<Integer, Projection.Player> result = new HashMap<>();
        for (PlayerEntity p : players) {
            int pos = nz(p.getPositionId());
            if (pos < 1 || pos > 4) {
                continue;   // managers or unknown types
            }
            var window = minutesWindow(p, playedByTeam.getOrDefault(p.getTeamId(), new TreeMap<>()), historyByKey);
            PointsModel.Rates rates = rates(p, pos, priors.get(pos), window,
                    defensivePointsTotal.getOrDefault(p.getId(), 0.0));

            List<Projection.Gameweek> gws = new ArrayList<>();
            PointsModel.Breakdown nextBreakdown = PointsModel.Breakdown.ZERO;
            double total = 0;
            double cleanSheetNext = 0;
            double multiplierSum = 0;
            int fixtureCount = 0;
            for (int i = 0; i < events.size(); i++) {
                int event = events.get(i);
                double availability = PointsModel.availability(p.getStatus(), p.getChanceOfPlaying(), i);
                PointsModel.Minutes minutes = window.minutes().scaled(availability);
                List<FixtureEntity> matches = upcoming.getOrDefault(p.getTeamId(), Map.of())
                        .getOrDefault(event, List.of());
                List<Projection.FixtureInfo> infos = new ArrayList<>();
                PointsModel.Breakdown gw = PointsModel.Breakdown.ZERO;
                for (FixtureEntity f : matches) {
                    boolean home = p.getTeamId().equals(f.getTeamH());
                    int opponent = home ? f.getTeamA() : f.getTeamH();
                    var context = strength.context(p.getTeamId(), opponent, home);
                    gw = gw.plus(PointsModel.expectedPoints(pos, rates, minutes, context));
                    infos.add(new Projection.FixtureInfo(opponent, teamNames.getOrDefault(opponent, "?"), home,
                            home ? f.getTeamHDifficulty() : f.getTeamADifficulty()));
                    multiplierSum += context.attackMultiplier();
                    fixtureCount++;
                    if (i == 0) {
                        cleanSheetNext = Math.max(cleanSheetNext, Math.exp(-context.goalsAgainst()));
                    }
                }
                if (i == 0) {
                    nextBreakdown = gw;
                }
                double points = round2(gw.total());
                total += points;
                gws.add(new Projection.Gameweek(event, infos, points));
            }

            double availabilityNow = PointsModel.availability(p.getStatus(), p.getChanceOfPlaying(), 0);
            var inputs = new Projection.Inputs(round2(rates.xg90()), round2(rates.xa90()), round2(rates.bonus90()),
                    round2(rates.defensiveContribution90()),
                    round2(window.minutes().playProbability()), round2(window.minutes().sixtyProbability()),
                    Math.round(window.minutes().expectedMinutes() * 10) / 10.0,
                    window.matches(), window.sixtyPlus(), availabilityNow, round2(cleanSheetNext),
                    fixtureCount == 0 ? 0 : round2(multiplierSum / fixtureCount));

            Projection.Player projection = new Projection.Player(p.getId(), p.getWebName(), p.getTeamId(),
                    teamNames.getOrDefault(p.getTeamId(), "?"), pos, POSITIONS[pos], nzd(p.getPrice()),
                    nz(p.getTotalPoints()), p.getStatus(), p.getChanceOfPlaying(), p.getNews(),
                    nzd(p.getSelectedByPercent()), p.getPenaltiesOrder(), gws, round2(total),
                    round(nextBreakdown), inputs, List.of());
            result.put(p.getId(), withReasons(projection, events));
        }
        return new Projection.Set(firstEvent, events, result, teamNames);
    }

    /** Projections sorted by total expected points, optionally for one position. */
    public List<Projection.Player> top(int horizon, String position, Double maxPrice, int limit) {
        var set = project(horizon);
        return set.players().values().stream()
                .filter(p -> position == null || position.isBlank() || p.position().equalsIgnoreCase(position))
                .filter(p -> maxPrice == null || p.price() <= maxPrice + 1e-9)
                .sorted(Comparator.comparingDouble(Projection.Player::total).reversed())
                .limit(Math.max(1, Math.min(limit, 700)))
                .toList();
    }

    // ---------------------------------------------------------------------------------------------

    record Window(PointsModel.Minutes minutes, int matches, int sixtyPlus,
                  double recentXg, double recentXa, double recentMinutes) {}

    /**
     * Minutes model: the team's last 6 played gameweeks, newest weighted 1, then 0.85, 0.85^2, ...
     *   P(plays)      = weighted share of matches with minutes > 0
     *   P(60+ mins)   = weighted share of matches with 60+ minutes
     *   E[minutes]    = weighted average minutes per match
     * In a double gameweek the minutes are split evenly over the two matches.
     */
    static Window minutesWindow(PlayerEntity p, TreeMap<Integer, Integer> teamEvents,
                                Map<Long, PlayerGameweekEntity> historyByKey) {
        double weightSum = 0, play = 0, sixty = 0, minutes = 0;
        double xg = 0, xa = 0, mins = 0;
        int matches = 0, sixtyPlus = 0;
        double weight = 1.0;
        for (var e : teamEvents.descendingMap().entrySet()) {
            if (matches >= PointsModel.MINUTES_WINDOW) {
                break;
            }
            int matchCount = e.getValue();
            PlayerGameweekEntity row = historyByKey.get(PlayerGameweekEntity.key(p.getId(), e.getKey()));
            double perMatch = row == null ? 0 : nz(row.getMinutes()) / (double) matchCount;
            weightSum += weight;
            if (perMatch > 0) play += weight;
            if (perMatch >= 60) {
                sixty += weight;
                sixtyPlus++;
            }
            minutes += weight * Math.min(90, perMatch);
            if (row != null) {
                xg += nzd(row.getExpectedGoals());
                xa += nzd(row.getExpectedAssists());
                mins += nz(row.getMinutes());
            }
            matches++;
            weight *= PointsModel.MINUTES_DECAY;
        }
        if (weightSum == 0) {
            return new Window(new PointsModel.Minutes(0, 0, 0), 0, 0, 0, 0, 0);
        }
        return new Window(new PointsModel.Minutes(play / weightSum, sixty / weightSum, minutes / weightSum),
                matches, sixtyPlus, xg, xa, mins);
    }

    /**
     * Per-90 rates, shrunk toward the position average.
     * Attacking rates blend the whole season (60%) with the last 6 matches (40%) to capture form.
     */
    static PointsModel.Rates rates(PlayerEntity p, int pos, PointsModel.Rates prior, Window w, double defensivePoints) {
        double minutes = nz(p.getMinutes());
        double xgSeason = PointsModel.shrunkPer90(nzd(p.getExpectedGoals()), minutes, prior.xg90());
        double xaSeason = PointsModel.shrunkPer90(nzd(p.getExpectedAssists()), minutes, prior.xa90());
        double xgRecent = PointsModel.shrunkPer90(w.recentXg(), w.recentMinutes(), prior.xg90());
        double xaRecent = PointsModel.shrunkPer90(w.recentXa(), w.recentMinutes(), prior.xa90());
        double s = PointsModel.SEASON_WEIGHT;
        return new PointsModel.Rates(
                s * xgSeason + (1 - s) * xgRecent,
                s * xaSeason + (1 - s) * xaRecent,
                PointsModel.shrunkPer90(nz(p.getBonus()), minutes, prior.bonus90()),
                pos == 1 ? PointsModel.shrunkPer90(nz(p.getSaves()), minutes, prior.saves90()) : 0,
                PointsModel.shrunkPer90(nz(p.getYellowCards()), minutes, prior.yellow90()),
                PointsModel.shrunkPer90(defensivePoints, minutes, prior.defensiveContribution90()));
    }

    /** Average per-90 rates of regular players at each position (the shrinkage prior). */
    static Map<Integer, PointsModel.Rates> positionPriors(List<PlayerEntity> players, Map<Integer, Double> defensivePoints) {
        Map<Integer, PointsModel.Rates> priors = new HashMap<>();
        for (int pos = 1; pos <= 4; pos++) {
            double mins = 0, xg = 0, xa = 0, bonus = 0, saves = 0, yellow = 0, dc = 0;
            for (PlayerEntity p : players) {
                if (nz(p.getPositionId()) != pos || nz(p.getMinutes()) < PRIOR_MIN_MINUTES) {
                    continue;
                }
                mins += p.getMinutes();
                xg += nzd(p.getExpectedGoals());
                xa += nzd(p.getExpectedAssists());
                bonus += nz(p.getBonus());
                saves += nz(p.getSaves());
                yellow += nz(p.getYellowCards());
                dc += defensivePoints.getOrDefault(p.getId(), 0.0);
            }
            double per90 = mins == 0 ? 0 : 90.0 / mins;
            priors.put(pos, new PointsModel.Rates(xg * per90, xa * per90, bonus * per90, saves * per90,
                    yellow * per90, dc * per90));
        }
        return priors;
    }

    /**
     * Season totals per team: goals from results; expected goals by adding up the xG of the team's
     * players in each played gameweek (and the opponent's xG for xG conceded).
     */
    static Map<Integer, TeamStrength.TeamTotals> teamTotals(java.util.Set<Integer> teamIds, List<FixtureEntity> fixtures,
                                                            List<PlayerGameweekEntity> history,
                                                            Map<Integer, PlayerEntity> players,
                                                            Map<Integer, TreeMap<Integer, Integer>> playedByTeam) {
        // xG per team per event, split evenly when a team plays twice
        Map<Integer, Map<Integer, Double>> xgByTeamEvent = new HashMap<>();
        for (PlayerGameweekEntity row : history) {
            PlayerEntity p = players.get(row.getPlayerId());
            if (p == null || row.getExpectedGoals() == null) {
                continue;
            }
            xgByTeamEvent.computeIfAbsent(p.getTeamId(), k -> new HashMap<>())
                    .merge(row.getEvent(), row.getExpectedGoals(), Double::sum);
        }

        Map<Integer, double[]> acc = new HashMap<>();   // games, gf, ga, xgf, xga
        for (Integer id : teamIds) {
            acc.put(id, new double[5]);
        }
        for (FixtureEntity f : fixtures) {
            if (!f.hasResult() || f.getEvent() == null) {
                continue;
            }
            double xgHome = perMatchXg(xgByTeamEvent, playedByTeam, f.getTeamH(), f.getEvent());
            double xgAway = perMatchXg(xgByTeamEvent, playedByTeam, f.getTeamA(), f.getEvent());
            add(acc, f.getTeamH(), f.getTeamHScore(), f.getTeamAScore(), xgHome, xgAway);
            add(acc, f.getTeamA(), f.getTeamAScore(), f.getTeamHScore(), xgAway, xgHome);
        }
        Map<Integer, TeamStrength.TeamTotals> totals = new HashMap<>();
        acc.forEach((id, a) -> totals.put(id, new TeamStrength.TeamTotals((int) a[0], a[1], a[2], a[3], a[4])));
        return totals;
    }

    private static double perMatchXg(Map<Integer, Map<Integer, Double>> xg, Map<Integer, TreeMap<Integer, Integer>> played,
                                     int team, int event) {
        double total = xg.getOrDefault(team, Map.of()).getOrDefault(event, 0.0);
        int matches = played.getOrDefault(team, new TreeMap<>()).getOrDefault(event, 1);
        return total / matches;
    }

    private static void add(Map<Integer, double[]> acc, int team, int gf, int ga, double xgf, double xga) {
        double[] a = acc.computeIfAbsent(team, k -> new double[5]);
        a[0]++;
        a[1] += gf;
        a[2] += ga;
        a[3] += xgf;
        a[4] += xga;
    }

    // ---------------------------------------------------------------------------------------------

    /** Short, human-readable reasons behind a projection. */
    static Projection.Player withReasons(Projection.Player p, List<Integer> events) {
        List<String> reasons = new ArrayList<>();
        var in = p.inputs();

        if (in.availability() < 1.0) {
            String news = p.news() == null || p.news().isBlank() ? "" : " (" + p.news() + ")";
            reasons.add(Math.round(in.availability() * 100) + "% chance of playing" + news);
        }
        if (in.matchesInWindow() > 0) {
            if (in.sixtyProbability() >= 0.8) {
                reasons.add("Nailed: 60+ mins in " + in.sixtyPlusInWindow() + " of last " + in.matchesInWindow() + " matches");
            } else if (in.sixtyProbability() < 0.5) {
                reasons.add("Rotation risk: 60+ mins in only " + in.sixtyPlusInWindow() + " of last " + in.matchesInWindow());
            }
        }
        if (p.penaltiesOrder() != null && p.penaltiesOrder() == 1) {
            reasons.add("First-choice penalty taker");
        }
        double xgi = in.xg90() + in.xa90();
        if (p.positionId() >= 2 && xgi >= 0.25) {
            reasons.add(String.format(Locale.ROOT, "Attacking threat: %.2f xG + xA per 90", xgi));
        }
        if (p.positionId() <= 2 && in.cleanSheetChanceNext() > 0) {
            reasons.add(String.format(Locale.ROOT, "Clean sheet chance next GW: %.0f%%", in.cleanSheetChanceNext() * 100));
        }
        if (in.attackMultiplierAvg() >= 1.1) {
            reasons.add(String.format(Locale.ROOT, "Kind fixtures: opponents concede %.0f%% more than average",
                    (in.attackMultiplierAvg() - 1) * 100));
        } else if (in.attackMultiplierAvg() > 0 && in.attackMultiplierAvg() <= 0.9) {
            reasons.add(String.format(Locale.ROOT, "Tough fixtures: opponents concede %.0f%% less than average",
                    (1 - in.attackMultiplierAvg()) * 100));
        }
        for (var gw : p.gameweeks()) {
            if (gw.fixtures().size() > 1) {
                reasons.add("Double gameweek in GW" + gw.event());
            } else if (gw.fixtures().isEmpty()) {
                reasons.add("No match in GW" + gw.event());
            }
        }
        return new Projection.Player(p.id(), p.name(), p.teamId(), p.team(), p.positionId(), p.position(), p.price(),
                p.totalPoints(), p.status(), p.chanceOfPlaying(), p.news(), p.selectedByPercent(), p.penaltiesOrder(),
                p.gameweeks(), p.total(), p.nextBreakdown(), p.inputs(), reasons);
    }

    private static PointsModel.Breakdown round(PointsModel.Breakdown b) {
        return new PointsModel.Breakdown(round2(b.appearance()), round2(b.goals()), round2(b.assists()),
                round2(b.cleanSheet()), round2(b.goalsConceded()), round2(b.saves()), round2(b.bonus()),
                round2(b.defensiveContribution()), round2(b.cards()));
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static double nzd(Double v) {
        return v == null ? 0 : v;
    }
}
