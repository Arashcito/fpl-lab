package com.arash.fpllab.prediction;

import com.arash.fpllab.entity.GameweekEntity;
import com.arash.fpllab.entity.PlayerEntity;
import com.arash.fpllab.entity.PlayerGameweekEntity;
import com.arash.fpllab.entity.PredictionSnapshotEntity;
import com.arash.fpllab.repository.GameweekRepository;
import com.arash.fpllab.repository.PlayerGameweekRepository;
import com.arash.fpllab.repository.PlayerRepository;
import com.arash.fpllab.repository.PredictionSnapshotRepository;
import com.arash.fpllab.storage.ArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.arash.fpllab.entity.PredictionSnapshotEntity.MODEL_FPL_LAB;
import static com.arash.fpllab.entity.PredictionSnapshotEntity.MODEL_FPL_OFFICIAL;

/**
 * Tracks how good the predictions are.
 *
 * 1. Before each deadline, saves every player's predicted points for the next gameweek,
 *    from our model and from FPL's own estimate (ep_next) as a benchmark.
 * 2. After the gameweek, compares both with the real points.
 */
@Service
public class AccuracyService {

    private static final Logger logger = LoggerFactory.getLogger(AccuracyService.class);
    static final int TOP_N = 10;

    private final PredictionService predictionService;
    private final PredictionSnapshotRepository snapshotRepository;
    private final GameweekRepository gameweekRepository;
    private final PlayerRepository playerRepository;
    private final PlayerGameweekRepository playerGameweekRepository;
    private final ArchiveService archiveService;
    private final ObjectMapper objectMapper;

    public AccuracyService(PredictionService predictionService, PredictionSnapshotRepository snapshotRepository,
                           GameweekRepository gameweekRepository, PlayerRepository playerRepository,
                           PlayerGameweekRepository playerGameweekRepository, ArchiveService archiveService,
                           ObjectMapper objectMapper) {
        this.predictionService = predictionService;
        this.snapshotRepository = snapshotRepository;
        this.gameweekRepository = gameweekRepository;
        this.playerRepository = playerRepository;
        this.playerGameweekRepository = playerGameweekRepository;
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
    }

    // ---- 1. saving predictions ----

    /**
     * Saves predictions for the next gameweek. Runs after every sync, so the last save before
     * the deadline (at most 30 minutes before) is what gets judged. After the deadline the
     * gameweek is no longer "next", so its predictions stay frozen.
     */
    public int snapshotNextGameweek() {
        var next = gameweekRepository.findFirstByNextTrue();
        if (next.isEmpty() || next.get().getDeadlineTime() == null) {
            return 0;
        }
        GameweekEntity gw = next.get();
        Instant now = Instant.now();
        if (!now.isBefore(gw.getDeadlineTime())) {
            return 0;
        }
        Projection.Set projections = predictionService.project(1);
        if (projections.firstEvent() != gw.getId()) {
            return 0;
        }
        List<PredictionSnapshotEntity> rows = new ArrayList<>();
        for (Projection.Player p : projections.players().values()) {
            rows.add(new PredictionSnapshotEntity(MODEL_FPL_LAB, gw.getId(), p.id(), p.pointsIn(0), now));
        }
        for (PlayerEntity p : playerRepository.findAll()) {
            if (p.getFplExpectedPoints() != null) {
                rows.add(new PredictionSnapshotEntity(MODEL_FPL_OFFICIAL, gw.getId(), p.getId(),
                        p.getFplExpectedPoints(), now));
            }
        }
        snapshotRepository.saveAll(rows);
        logger.info("Saved {} predictions for GW{}", rows.size(), gw.getId());
        archivePredictions(gw.getId(), now, projections);
        return rows.size();
    }

    /** One JSON file per gameweek in object storage; overwritten until the deadline, then final. */
    public record ArchivedPlayer(int id, String name, String team, String position, double price,
                                 double fplLab, Double fplOfficial) {}

    public record ArchivedPredictions(int event, Instant savedAt, List<String> models, List<ArchivedPlayer> players) {}

    private void archivePredictions(int event, Instant savedAt, Projection.Set projections) {
        if (!archiveService.isEnabled()) {
            return;
        }
        try {
            Map<Integer, PlayerEntity> players = playersById();
            List<ArchivedPlayer> list = projections.players().values().stream()
                    .sorted(Comparator.comparingDouble((Projection.Player p) -> p.pointsIn(0)).reversed())
                    .map(p -> new ArchivedPlayer(p.id(), p.name(), p.team(), p.position(), p.price(),
                            p.pointsIn(0), players.containsKey(p.id()) ? players.get(p.id()).getFplExpectedPoints() : null))
                    .toList();
            var archive = new ArchivedPredictions(event, savedAt, List.of(MODEL_FPL_LAB, MODEL_FPL_OFFICIAL), list);
            archiveService.putJson(ArchiveService.predictionsKey(event), objectMapper.writeValueAsString(archive));
        } catch (Exception e) {
            logger.warn("Could not archive GW{} predictions to object storage", event, e);
        }
    }

    /** Short-lived link to download a gameweek's archived predictions, if storage has them. */
    public java.util.Optional<String> predictionsDownloadLink(int event) {
        return archiveService.downloadLink(ArchiveService.predictionsKey(event));
    }

    // ---- 2. scoring ----

    /** One prediction next to what really happened. */
    public record Pair(int playerId, double predicted, double actual) {}

    /**
     * How good one model was in one gameweek. Only players who played count.
     *
     * @param meanAbsoluteError average of |predicted - actual| (lower is better)
     * @param bias              average of (predicted - actual): above 0 = too optimistic
     * @param rankCorrelation   Spearman correlation between predicted and actual order (1 = perfect order)
     * @param topPicksAverage   average real points of the model's 10 highest-predicted players
     */
    public record Score(String model, int players, double meanAbsoluteError, double bias,
                        double rankCorrelation, double topPicksAverage,
                        Integer captainId, String captainName, double captainPredicted, double captainActual) {}

    public record GameweekSummary(int event, String status, Instant savedAt, Score fplLab, Score fplOfficial,
                                  Integer bestPlayerId, String bestPlayerName, int bestPlayerPoints) {}

    public record Overall(int gameweeksScored, Double fplLabError, Double fplOfficialError,
                          Double fplLabTopPicks, Double fplOfficialTopPicks, int fplLabWins, int fplOfficialWins) {}

    public record Report(Overall overall, List<GameweekSummary> gameweeks) {}

    public record PlayerRow(int playerId, String name, String team, String position,
                            Double fplLab, Double fplOfficial, Integer actual, Integer minutes) {}

    public record GameweekDetail(GameweekSummary summary, List<PlayerRow> topPredicted,
                                 List<PlayerRow> overPredicted, List<PlayerRow> underPredicted) {}

    public Report report() {
        Map<Integer, List<PredictionSnapshotEntity>> byEvent = snapshotRepository.findAll().stream()
                .collect(Collectors.groupingBy(PredictionSnapshotEntity::getEvent, TreeMap::new, Collectors.toList()));
        Map<Integer, PlayerEntity> players = playersById();
        Map<Integer, GameweekEntity> gameweeks = gameweekRepository.findAll().stream()
                .collect(Collectors.toMap(GameweekEntity::getId, g -> g));

        List<GameweekSummary> summaries = new ArrayList<>();
        for (var e : byEvent.entrySet()) {
            summaries.add(summarise(e.getKey(), e.getValue(), gameweeks.get(e.getKey()), players));
        }
        summaries.sort(Comparator.comparingInt(GameweekSummary::event).reversed());
        return new Report(overall(summaries), summaries);
    }

    public GameweekDetail detail(int event) {
        List<PredictionSnapshotEntity> snapshots = snapshotRepository.findByEvent(event);
        if (snapshots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No predictions were saved for GW" + event);
        }
        Map<Integer, PlayerEntity> players = playersById();
        GameweekEntity gw = gameweekRepository.findById(event).orElse(null);
        GameweekSummary summary = summarise(event, snapshots, gw, players);

        Map<Integer, Double> lab = predictions(snapshots, MODEL_FPL_LAB);
        Map<Integer, Double> official = predictions(snapshots, MODEL_FPL_OFFICIAL);
        Map<Integer, PlayerGameweekEntity> actual = actuals(event);

        List<PlayerRow> rows = new ArrayList<>();
        for (var e : lab.entrySet()) {
            PlayerEntity p = players.get(e.getKey());
            PlayerGameweekEntity a = actual.get(e.getKey());
            rows.add(new PlayerRow(e.getKey(), p == null ? "?" : p.getWebName(), teamShort(p), position(p),
                    round(e.getValue()), official.containsKey(e.getKey()) ? round(official.get(e.getKey())) : null,
                    a == null ? null : a.getTotalPoints(), a == null ? null : a.getMinutes()));
        }
        List<PlayerRow> top = rows.stream()
                .sorted(Comparator.comparingDouble((PlayerRow r) -> r.fplLab()).reversed())
                .limit(15).toList();
        List<PlayerRow> played = rows.stream().filter(r -> r.minutes() != null && r.minutes() > 0).toList();
        List<PlayerRow> over = played.stream()
                .sorted(Comparator.comparingDouble((PlayerRow r) -> r.fplLab() - r.actual()).reversed())
                .limit(5).toList();
        List<PlayerRow> under = played.stream()
                .sorted(Comparator.comparingDouble((PlayerRow r) -> r.actual() - r.fplLab()).reversed())
                .limit(5).toList();
        return new GameweekDetail(summary, top, over, under);
    }

    private GameweekSummary summarise(int event, List<PredictionSnapshotEntity> snapshots, GameweekEntity gw,
                                      Map<Integer, PlayerEntity> players) {
        String status = status(gw);
        Instant savedAt = snapshots.stream().map(PredictionSnapshotEntity::getSavedAt)
                .max(Comparator.naturalOrder()).orElse(null);
        if (status.equals("upcoming")) {
            return new GameweekSummary(event, status, savedAt, null, null, null, null, 0);
        }
        Map<Integer, PlayerGameweekEntity> actual = actuals(event);
        Score lab = score(MODEL_FPL_LAB, predictions(snapshots, MODEL_FPL_LAB), actual, players);
        Score official = score(MODEL_FPL_OFFICIAL, predictions(snapshots, MODEL_FPL_OFFICIAL), actual, players);

        PlayerGameweekEntity best = actual.values().stream()
                .max(Comparator.comparingInt(PlayerGameweekEntity::getTotalPoints)).orElse(null);
        return new GameweekSummary(event, status, savedAt, lab, official,
                best == null ? null : best.getPlayerId(),
                best == null ? null : name(players.get(best.getPlayerId())),
                best == null ? 0 : best.getTotalPoints());
    }

    private static Score score(String model, Map<Integer, Double> predictions,
                               Map<Integer, PlayerGameweekEntity> actual, Map<Integer, PlayerEntity> players) {
        List<Pair> played = new ArrayList<>();
        List<Pair> all = new ArrayList<>();
        for (var e : predictions.entrySet()) {
            PlayerGameweekEntity a = actual.get(e.getKey());
            int points = a == null ? 0 : a.getTotalPoints();
            Pair pair = new Pair(e.getKey(), e.getValue(), points);
            all.add(pair);
            if (a != null && a.getMinutes() != null && a.getMinutes() > 0) {
                played.add(pair);
            }
        }
        if (all.isEmpty()) {
            return null;
        }
        Pair captain = all.stream().max(Comparator.comparingDouble(Pair::predicted)).orElse(null);
        return new Score(model, played.size(),
                round(meanAbsoluteError(played)), round(bias(played)), round(rankCorrelation(played)),
                round(topPicksAverage(all, TOP_N)),
                captain.playerId(), name(players.get(captain.playerId())),
                round(captain.predicted()), captain.actual());
    }

    static Overall overall(List<GameweekSummary> summaries) {
        int n = 0, labWins = 0, officialWins = 0;
        double labError = 0, officialError = 0, labTop = 0, officialTop = 0;
        for (GameweekSummary s : summaries) {
            if (!"finished".equals(s.status()) || s.fplLab() == null || s.fplOfficial() == null) {
                continue;
            }
            n++;
            labError += s.fplLab().meanAbsoluteError();
            officialError += s.fplOfficial().meanAbsoluteError();
            labTop += s.fplLab().topPicksAverage();
            officialTop += s.fplOfficial().topPicksAverage();
            if (s.fplLab().meanAbsoluteError() < s.fplOfficial().meanAbsoluteError()) labWins++;
            else if (s.fplOfficial().meanAbsoluteError() < s.fplLab().meanAbsoluteError()) officialWins++;
        }
        if (n == 0) {
            return new Overall(0, null, null, null, null, 0, 0);
        }
        return new Overall(n, round(labError / n), round(officialError / n), round(labTop / n),
                round(officialTop / n), labWins, officialWins);
    }

    // ---- metrics (pure functions) ----

    static double meanAbsoluteError(List<Pair> pairs) {
        return pairs.stream().mapToDouble(p -> Math.abs(p.predicted() - p.actual())).average().orElse(0);
    }

    static double bias(List<Pair> pairs) {
        return pairs.stream().mapToDouble(p -> p.predicted() - p.actual()).average().orElse(0);
    }

    /** Average real points of the n players with the highest predictions. */
    static double topPicksAverage(List<Pair> pairs, int n) {
        return pairs.stream()
                .sorted(Comparator.comparingDouble(Pair::predicted).reversed())
                .limit(n)
                .mapToDouble(Pair::actual)
                .average().orElse(0);
    }

    /** Spearman rank correlation: Pearson correlation of the ranks (ties share their average rank). */
    static double rankCorrelation(List<Pair> pairs) {
        if (pairs.size() < 3) {
            return 0;
        }
        double[] rp = ranks(pairs.stream().mapToDouble(Pair::predicted).toArray());
        double[] ra = ranks(pairs.stream().mapToDouble(Pair::actual).toArray());
        return pearson(rp, ra);
    }

    static double[] ranks(double[] values) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[values.length];
        int i = 0;
        while (i < order.length) {
            int j = i;
            while (j + 1 < order.length && values[order[j + 1]] == values[order[i]]) j++;
            double avg = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) ranks[order[k]] = avg;
            i = j + 1;
        }
        return ranks;
    }

    static double pearson(double[] x, double[] y) {
        double mx = java.util.Arrays.stream(x).average().orElse(0);
        double my = java.util.Arrays.stream(y).average().orElse(0);
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < x.length; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        return sxx == 0 || syy == 0 ? 0 : sxy / Math.sqrt(sxx * syy);
    }

    // ---- helpers ----

    private static String status(GameweekEntity gw) {
        if (gw == null) return "unknown";
        if (Boolean.TRUE.equals(gw.getFinished())) return "finished";
        if (Boolean.TRUE.equals(gw.getCurrent())) return "in progress";
        return "upcoming";
    }

    private static Map<Integer, Double> predictions(List<PredictionSnapshotEntity> snapshots, String model) {
        Map<Integer, Double> map = new HashMap<>();
        for (PredictionSnapshotEntity s : snapshots) {
            if (model.equals(s.getModel())) map.put(s.getPlayerId(), s.getPredictedPoints());
        }
        return map;
    }

    private Map<Integer, PlayerGameweekEntity> actuals(int event) {
        return playerGameweekRepository.findByEvent(event).stream()
                .collect(Collectors.toMap(PlayerGameweekEntity::getPlayerId, r -> r, (a, b) -> a));
    }

    private Map<Integer, PlayerEntity> playersById() {
        return playerRepository.findAll().stream().collect(Collectors.toMap(PlayerEntity::getId, p -> p));
    }

    private static String name(PlayerEntity p) {
        return p == null ? "?" : p.getWebName();
    }

    private String teamShort(PlayerEntity p) {
        if (p == null) return "?";
        return predictionService.project(1).teamShortNames().getOrDefault(p.getTeamId(), "?");
    }

    private static String position(PlayerEntity p) {
        if (p == null || p.getPositionId() == null) return "?";
        return switch (p.getPositionId()) {
            case 1 -> "GKP";
            case 2 -> "DEF";
            case 3 -> "MID";
            case 4 -> "FWD";
            default -> "?";
        };
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
