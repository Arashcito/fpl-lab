package com.arash.fpllab.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A prediction saved before a gameweek deadline, so it can be compared with
 * the real points once the gameweek is played. One row per model, gameweek and player.
 */
@Entity
@Table(name = "prediction_snapshots", indexes = {
        @Index(name = "idx_snapshot_event", columnList = "event")
})
public class PredictionSnapshotEntity {

    /** Our formula-based model. */
    public static final String MODEL_FPL_LAB = "fpl-lab";
    /** FPL's own "expected points" (ep_next), used as a benchmark. */
    public static final String MODEL_FPL_OFFICIAL = "fpl-official";
    /** The machine-learning model (Python, ml/), read from the ml_predictions table. */
    public static final String MODEL_FPL_LAB_ML = "fpl-lab-ml";

    // "<model>-<event>-<playerId>", so re-saving before the deadline overwrites the same row
    @Id
    private String id;

    private String model;
    private Integer event;
    private Integer playerId;
    private Double predictedPoints;
    private Instant savedAt;

    public PredictionSnapshotEntity() {}

    public PredictionSnapshotEntity(String model, int event, int playerId, double predictedPoints, Instant savedAt) {
        this.id = key(model, event, playerId);
        this.model = model;
        this.event = event;
        this.playerId = playerId;
        this.predictedPoints = predictedPoints;
        this.savedAt = savedAt;
    }

    public static String key(String model, int event, int playerId) {
        return model + "-" + event + "-" + playerId;
    }

    public String getId() { return id; }
    public String getModel() { return model; }
    public Integer getEvent() { return event; }
    public Integer getPlayerId() { return playerId; }
    public Double getPredictedPoints() { return predictedPoints; }
    public Instant getSavedAt() { return savedAt; }
}
