package com.arash.fpllab.repository;

import com.arash.fpllab.entity.FixtureEntity;
import com.arash.fpllab.entity.PlayerEntity;
import com.arash.fpllab.entity.PlayerGameweekEntity;
import com.arash.fpllab.entity.PredictionSnapshotEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Fast bulk "insert or update" for the big sync tables, using Postgres
 * INSERT ... ON CONFLICT (id) DO UPDATE in JDBC batches.
 *
 * Saving through JPA one row at a time costs a SELECT plus an INSERT/UPDATE per row
 * (about 19,000 round trips on a first sync). That is fine next to a local database
 * but takes 10+ minutes against a cloud database 40 ms away. Batched upserts need a
 * handful of round trips instead.
 *
 * Column names follow Hibernate's default naming of the entities (e.g. teamHScore -> teamhscore).
 */
@Repository
public class BulkWriter {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;

    public BulkWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void upsertPlayers(List<PlayerEntity> players) {
        upsert("players", List.of("id", "web_name", "team_id", "position_id", "price", "total_points", "status",
                        "chance_of_playing", "news", "minutes", "starts", "goals", "assists", "expected_goals",
                        "expected_assists", "expected_goals_conceded", "saves", "bonus", "yellow_cards",
                        "penalties_order", "form", "selected_by_percent", "fpl_expected_points"),
                players, p -> new Object[]{p.getId(), p.getWebName(), p.getTeamId(), p.getPositionId(), p.getPrice(),
                        p.getTotalPoints(), p.getStatus(), p.getChanceOfPlaying(), p.getNews(), p.getMinutes(),
                        p.getStarts(), p.getGoals(), p.getAssists(), p.getExpectedGoals(), p.getExpectedAssists(),
                        p.getExpectedGoalsConceded(), p.getSaves(), p.getBonus(), p.getYellowCards(),
                        p.getPenaltiesOrder(), p.getForm(), p.getSelectedByPercent(), p.getFplExpectedPoints()});
    }

    @Transactional
    public void upsertPlayerGameweeks(List<PlayerGameweekEntity> rows) {
        upsert("player_gameweeks", List.of("id", "player_id", "event", "minutes", "starts", "goals", "assists",
                        "clean_sheets", "goals_conceded", "saves", "bonus", "yellow_cards", "expected_goals",
                        "expected_assists", "expected_goals_conceded", "defensive_contribution_points", "total_points"),
                rows, r -> new Object[]{r.getId(), r.getPlayerId(), r.getEvent(), r.getMinutes(), r.getStarts(),
                        r.getGoals(), r.getAssists(), r.getCleanSheets(), r.getGoalsConceded(), r.getSaves(),
                        r.getBonus(), r.getYellowCards(), r.getExpectedGoals(), r.getExpectedAssists(),
                        r.getExpectedGoalsConceded(), r.getDefensiveContributionPoints(), r.getTotalPoints()});
    }

    @Transactional
    public void upsertFixtures(List<FixtureEntity> fixtures) {
        upsert("fixtures", List.of("id", "event", "kickoff_time", "teamh", "teama", "teamhscore", "teamascore",
                        "teamhdifficulty", "teamadifficulty", "started", "finished"),
                fixtures, f -> new Object[]{f.getId(), f.getEvent(),
                        f.getKickoffTime() == null ? null : Timestamp.from(f.getKickoffTime()),
                        f.getTeamH(), f.getTeamA(), f.getTeamHScore(), f.getTeamAScore(),
                        f.getTeamHDifficulty(), f.getTeamADifficulty(), f.getStarted(), f.getFinished()});
    }

    @Transactional
    public void upsertSnapshots(List<PredictionSnapshotEntity> rows) {
        upsert("prediction_snapshots", List.of("id", "model", "event", "player_id", "predicted_points", "saved_at"),
                rows, s -> new Object[]{s.getId(), s.getModel(), s.getEvent(), s.getPlayerId(), s.getPredictedPoints(),
                        s.getSavedAt() == null ? null : Timestamp.from(s.getSavedAt())});
    }

    interface RowMapper<T> {
        Object[] values(T row);
    }

    static String upsertSql(String table, List<String> columns) {
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
        String updates = String.join(", ", columns.stream().skip(1).map(c -> c + " = EXCLUDED." + c).toList());
        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")"
                + " ON CONFLICT (id) DO UPDATE SET " + updates;
    }

    private <T> void upsert(String table, List<String> columns, List<T> rows, RowMapper<T> mapper) {
        if (rows.isEmpty()) {
            return;
        }
        String sql = upsertSql(table, columns);
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (T row : rows) {
            batch.add(mapper.values(row));
            if (batch.size() == BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }
    }
}
