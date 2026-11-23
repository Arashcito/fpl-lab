package com.arash.fpllab.service;

import com.arash.fpllab.client.FplClient;
import com.arash.fpllab.entity.FixtureEntity;
import com.arash.fpllab.entity.GameweekEntity;
import com.arash.fpllab.entity.PlayerEntity;
import com.arash.fpllab.entity.PlayerGameweekEntity;
import com.arash.fpllab.entity.TeamEntity;
import com.arash.fpllab.prediction.AccuracyService;
import com.arash.fpllab.repository.FixtureRepository;
import com.arash.fpllab.repository.GameweekRepository;
import com.arash.fpllab.repository.PlayerGameweekRepository;
import com.arash.fpllab.repository.PlayerRepository;
import com.arash.fpllab.repository.TeamRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copies FPL data into Postgres so the API and the prediction model read from the database:
 * teams, players, gameweeks, fixtures and every player's per-gameweek stats.
 */
@Service
public class SyncService {

    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);

    private final FplClient fplClient;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final GameweekRepository gameweekRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerGameweekRepository playerGameweekRepository;
    private final CacheManager cacheManager;
    private final AccuracyService accuracyService;
    private final boolean syncOnStartup;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant lastSync;

    public SyncService(FplClient fplClient,
                       TeamRepository teamRepository,
                       PlayerRepository playerRepository,
                       GameweekRepository gameweekRepository,
                       FixtureRepository fixtureRepository,
                       PlayerGameweekRepository playerGameweekRepository,
                       CacheManager cacheManager,
                       AccuracyService accuracyService,
                       @Value("${fpl.sync.on-startup:true}") boolean syncOnStartup) {
        this.fplClient = fplClient;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.gameweekRepository = gameweekRepository;
        this.fixtureRepository = fixtureRepository;
        this.playerGameweekRepository = playerGameweekRepository;
        this.cacheManager = cacheManager;
        this.accuracyService = accuracyService;
        this.syncOnStartup = syncOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (syncOnStartup) {
            Thread.ofVirtual().name("fpl-startup-sync").start(this::syncAllSafely);
        }
    }

    // Every 30 minutes: prices, injuries, results and the live gameweek.
    @Scheduled(cron = "${fpl.sync.cron:0 */30 * * * *}")
    public void scheduledSync() {
        syncAllSafely();
    }

    public void syncAllSafely() {
        try {
            syncAll();
        } catch (Exception e) {
            logger.error("FPL sync failed", e);
        }
    }

    /**
     * Full sync. Finished gameweeks are only downloaded once; the current one is refreshed every run.
     * Returns a short summary, or null when a sync is already running.
     */
    public SyncResult syncAll() {
        if (!running.compareAndSet(false, true)) {
            return null;
        }
        try {
            long start = System.currentTimeMillis();
            evict("bootstrap");
            JsonNode bootstrap = fplClient.getBootstrapStatic();

            int teams = syncTeams(bootstrap);
            int players = syncPlayers(bootstrap);
            List<GameweekEntity> gameweeks = syncGameweeks(bootstrap);
            int fixtures = syncFixtures();
            int gameweeksSynced = syncPlayerGameweeks(gameweeks);

            evict("projections");
            snapshotPredictions();
            lastSync = Instant.now();
            SyncResult result = new SyncResult(teams, players, fixtures, gameweeksSynced,
                    System.currentTimeMillis() - start, lastSync);
            logger.info("FPL sync done: {}", result);
            return result;
        } finally {
            running.set(false);
        }
    }

    // Saves predictions for the next deadline; a failure here must not break the sync itself.
    private void snapshotPredictions() {
        try {
            accuracyService.snapshotNextGameweek();
        } catch (Exception e) {
            logger.warn("Could not save predictions for the accuracy page", e);
        }
    }

    public Instant getLastSync() {
        return lastSync;
    }

    public int syncTeams(JsonNode bootstrap) {
        List<TeamEntity> teams = new ArrayList<>();
        for (JsonNode t : bootstrap.get("teams")) {
            teams.add(new TeamEntity(t.get("id").asInt(), t.get("name").asText(), t.get("short_name").asText()));
        }
        teamRepository.saveAll(teams);
        return teams.size();
    }

    public int syncPlayers(JsonNode bootstrap) {
        List<PlayerEntity> players = new ArrayList<>();
        for (JsonNode p : bootstrap.get("elements")) {
            PlayerEntity e = new PlayerEntity(
                    p.get("id").asInt(),
                    p.get("web_name").asText(),
                    p.get("team").asInt(),
                    p.get("element_type").asInt(),
                    p.get("now_cost").asInt() / 10.0,
                    p.get("total_points").asInt());
            e.setStatus(p.path("status").asText("a"));
            e.setChanceOfPlaying(intOrNull(p.get("chance_of_playing_next_round")));
            e.setNews(p.path("news").asText(""));
            e.setMinutes(p.path("minutes").asInt());
            e.setStarts(p.path("starts").asInt());
            e.setGoals(p.path("goals_scored").asInt());
            e.setAssists(p.path("assists").asInt());
            e.setExpectedGoals(p.path("expected_goals").asDouble());
            e.setExpectedAssists(p.path("expected_assists").asDouble());
            e.setExpectedGoalsConceded(p.path("expected_goals_conceded").asDouble());
            e.setSaves(p.path("saves").asInt());
            e.setBonus(p.path("bonus").asInt());
            e.setYellowCards(p.path("yellow_cards").asInt());
            e.setPenaltiesOrder(intOrNull(p.get("penalties_order")));
            e.setForm(p.path("form").asDouble());
            e.setSelectedByPercent(p.path("selected_by_percent").asDouble());
            e.setFplExpectedPoints(p.path("ep_next").isNull() ? null : p.path("ep_next").asDouble());
            players.add(e);
        }
        playerRepository.saveAll(players);
        return players.size();
    }

    public List<GameweekEntity> syncGameweeks(JsonNode bootstrap) {
        List<GameweekEntity> gameweeks = new ArrayList<>();
        for (JsonNode ev : bootstrap.get("events")) {
            GameweekEntity g = new GameweekEntity();
            g.setId(ev.get("id").asInt());
            g.setName(ev.path("name").asText());
            g.setDeadlineTime(instantOrNull(ev.get("deadline_time")));
            g.setFinished(ev.path("finished").asBoolean());
            g.setCurrent(ev.path("is_current").asBoolean());
            g.setNext(ev.path("is_next").asBoolean());
            gameweeks.add(g);
        }
        return gameweekRepository.saveAll(gameweeks);
    }

    public int syncFixtures() {
        evict("fixtures");
        List<FixtureEntity> fixtures = new ArrayList<>();
        for (JsonNode f : fplClient.getFixtures()) {
            FixtureEntity e = new FixtureEntity();
            e.setId(f.get("id").asInt());
            e.setEvent(intOrNull(f.get("event")));
            e.setKickoffTime(instantOrNull(f.get("kickoff_time")));
            e.setTeamH(f.get("team_h").asInt());
            e.setTeamA(f.get("team_a").asInt());
            e.setTeamHScore(intOrNull(f.get("team_h_score")));
            e.setTeamAScore(intOrNull(f.get("team_a_score")));
            e.setTeamHDifficulty(intOrNull(f.get("team_h_difficulty")));
            e.setTeamADifficulty(intOrNull(f.get("team_a_difficulty")));
            e.setStarted(f.path("started").asBoolean());
            // count a result once the final whistle has gone, without waiting for bonus confirmation
            e.setFinished(f.path("finished").asBoolean() || f.path("finished_provisional").asBoolean());
            fixtures.add(e);
        }
        fixtureRepository.saveAll(fixtures);
        return fixtures.size();
    }

    /**
     * Downloads per-player stats for every played gameweek not yet in the database,
     * and always refreshes the current gameweek (its matches may still be in progress).
     */
    public int syncPlayerGameweeks(List<GameweekEntity> gameweeks) {
        Set<Integer> synced = playerGameweekRepository.findSyncedEvents();
        int count = 0;
        for (GameweekEntity g : gameweeks) {
            boolean played = Boolean.TRUE.equals(g.getFinished()) || Boolean.TRUE.equals(g.getCurrent());
            if (!played) {
                continue;
            }
            if (synced.contains(g.getId()) && !Boolean.TRUE.equals(g.getCurrent())) {
                continue;
            }
            savePlayerGameweeks(g.getId(), fplClient.getEventLive(g.getId()));
            count++;
        }
        return count;
    }

    public void savePlayerGameweeks(int event, JsonNode live) {
        List<PlayerGameweekEntity> rows = new ArrayList<>();
        for (JsonNode el : live.path("elements")) {
            JsonNode s = el.path("stats");
            int playerId = el.get("id").asInt();
            PlayerGameweekEntity r = new PlayerGameweekEntity();
            r.setId(PlayerGameweekEntity.key(playerId, event));
            r.setPlayerId(playerId);
            r.setEvent(event);
            r.setMinutes(s.path("minutes").asInt());
            r.setStarts(s.path("starts").asInt());
            r.setGoals(s.path("goals_scored").asInt());
            r.setAssists(s.path("assists").asInt());
            r.setCleanSheets(s.path("clean_sheets").asInt());
            r.setGoalsConceded(s.path("goals_conceded").asInt());
            r.setSaves(s.path("saves").asInt());
            r.setBonus(s.path("bonus").asInt());
            r.setYellowCards(s.path("yellow_cards").asInt());
            r.setExpectedGoals(s.path("expected_goals").asDouble());
            r.setExpectedAssists(s.path("expected_assists").asDouble());
            r.setExpectedGoalsConceded(s.path("expected_goals_conceded").asDouble());
            r.setDefensiveContributionPoints(pointsFor(el.path("explain"), "defensive_contribution"));
            r.setTotalPoints(s.path("total_points").asInt());
            rows.add(r);
        }
        playerGameweekRepository.saveAll(rows);
    }

    // Sums the FPL points awarded for one stat across a gameweek's matches.
    private static int pointsFor(JsonNode explain, String identifier) {
        int points = 0;
        for (JsonNode fixture : explain) {
            for (JsonNode stat : fixture.path("stats")) {
                if (identifier.equals(stat.path("identifier").asText())) {
                    points += stat.path("points").asInt();
                }
            }
        }
        return points;
    }

    private void evict(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private static Integer intOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asInt();
    }

    private static Instant instantOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : Instant.parse(n.asText());
    }

    public record SyncResult(int teams, int players, int fixtures, int gameweeksDownloaded,
                             long durationMs, Instant finishedAt) {}
}
