package com.arash.fpllab.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One player's stats for one gameweek, synced from FPL /event/{gw}/live/.
 * In a double gameweek the stats cover both matches.
 */
@Entity
@Table(name = "player_gameweeks", indexes = {
        @Index(name = "idx_pgw_event", columnList = "event"),
        @Index(name = "idx_pgw_player", columnList = "playerId")
})
public class PlayerGameweekEntity {

    // playerId * 100 + event, so a re-sync overwrites the same row
    @Id
    private Long id;

    private Integer playerId;
    private Integer event;
    private Integer minutes;
    private Integer starts;
    private Integer goals;
    private Integer assists;
    private Integer cleanSheets;
    private Integer goalsConceded;
    private Integer saves;
    private Integer bonus;
    private Integer yellowCards;
    private Double expectedGoals;
    private Double expectedAssists;
    private Double expectedGoalsConceded;
    private Integer defensiveContributionPoints;
    private Integer totalPoints;

    public PlayerGameweekEntity() {}

    public static long key(int playerId, int event) {
        return playerId * 100L + event;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getPlayerId() { return playerId; }
    public void setPlayerId(Integer playerId) { this.playerId = playerId; }

    public Integer getEvent() { return event; }
    public void setEvent(Integer event) { this.event = event; }

    public Integer getMinutes() { return minutes; }
    public void setMinutes(Integer minutes) { this.minutes = minutes; }

    public Integer getStarts() { return starts; }
    public void setStarts(Integer starts) { this.starts = starts; }

    public Integer getGoals() { return goals; }
    public void setGoals(Integer goals) { this.goals = goals; }

    public Integer getAssists() { return assists; }
    public void setAssists(Integer assists) { this.assists = assists; }

    public Integer getCleanSheets() { return cleanSheets; }
    public void setCleanSheets(Integer cleanSheets) { this.cleanSheets = cleanSheets; }

    public Integer getGoalsConceded() { return goalsConceded; }
    public void setGoalsConceded(Integer goalsConceded) { this.goalsConceded = goalsConceded; }

    public Integer getSaves() { return saves; }
    public void setSaves(Integer saves) { this.saves = saves; }

    public Integer getBonus() { return bonus; }
    public void setBonus(Integer bonus) { this.bonus = bonus; }

    public Integer getYellowCards() { return yellowCards; }
    public void setYellowCards(Integer yellowCards) { this.yellowCards = yellowCards; }

    public Double getExpectedGoals() { return expectedGoals; }
    public void setExpectedGoals(Double expectedGoals) { this.expectedGoals = expectedGoals; }

    public Double getExpectedAssists() { return expectedAssists; }
    public void setExpectedAssists(Double expectedAssists) { this.expectedAssists = expectedAssists; }

    public Double getExpectedGoalsConceded() { return expectedGoalsConceded; }
    public void setExpectedGoalsConceded(Double expectedGoalsConceded) { this.expectedGoalsConceded = expectedGoalsConceded; }

    public Integer getDefensiveContributionPoints() { return defensiveContributionPoints; }
    public void setDefensiveContributionPoints(Integer defensiveContributionPoints) { this.defensiveContributionPoints = defensiveContributionPoints; }

    public Integer getTotalPoints() { return totalPoints; }
    public void setTotalPoints(Integer totalPoints) { this.totalPoints = totalPoints; }
}
