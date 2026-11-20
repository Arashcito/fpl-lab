package com.arash.fpllab.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A Premier League fixture, synced from FPL /fixtures/.
 * event is null while a match is unscheduled (postponed).
 */
@Entity
@Table(name = "fixtures")
public class FixtureEntity {

    @Id
    private Integer id;

    private Integer event;
    private Instant kickoffTime;
    private Integer teamH;
    private Integer teamA;
    private Integer teamHScore;
    private Integer teamAScore;
    private Integer teamHDifficulty;
    private Integer teamADifficulty;
    private Boolean started;
    private Boolean finished;

    public FixtureEntity() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getEvent() { return event; }
    public void setEvent(Integer event) { this.event = event; }

    public Instant getKickoffTime() { return kickoffTime; }
    public void setKickoffTime(Instant kickoffTime) { this.kickoffTime = kickoffTime; }

    public Integer getTeamH() { return teamH; }
    public void setTeamH(Integer teamH) { this.teamH = teamH; }

    public Integer getTeamA() { return teamA; }
    public void setTeamA(Integer teamA) { this.teamA = teamA; }

    public Integer getTeamHScore() { return teamHScore; }
    public void setTeamHScore(Integer teamHScore) { this.teamHScore = teamHScore; }

    public Integer getTeamAScore() { return teamAScore; }
    public void setTeamAScore(Integer teamAScore) { this.teamAScore = teamAScore; }

    public Integer getTeamHDifficulty() { return teamHDifficulty; }
    public void setTeamHDifficulty(Integer teamHDifficulty) { this.teamHDifficulty = teamHDifficulty; }

    public Integer getTeamADifficulty() { return teamADifficulty; }
    public void setTeamADifficulty(Integer teamADifficulty) { this.teamADifficulty = teamADifficulty; }

    public Boolean getStarted() { return started; }
    public void setStarted(Boolean started) { this.started = started; }

    public Boolean getFinished() { return finished; }
    public void setFinished(Boolean finished) { this.finished = finished; }

    public boolean hasResult() {
        return Boolean.TRUE.equals(finished) && teamHScore != null && teamAScore != null;
    }
}
