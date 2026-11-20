package com.arash.fpllab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A player with season-to-date stats, synced from FPL bootstrap-static.
 */
@Entity
@Table(name = "players")
public class PlayerEntity {

    @Id
    private Integer id;

    private String webName;
    private Integer teamId;
    private Integer positionId;
    private Double price;
    private Integer totalPoints;

    // availability: a = available, d = doubtful, i = injured, s = suspended, u = unavailable, n = not in squad
    private String status;
    private Integer chanceOfPlaying;   // null means no flag (fully available)
    @Column(length = 1000)
    private String news;

    private Integer minutes;
    private Integer starts;
    private Integer goals;
    private Integer assists;
    private Double expectedGoals;
    private Double expectedAssists;
    private Double expectedGoalsConceded;
    private Integer saves;
    private Integer bonus;
    private Integer yellowCards;
    private Integer penaltiesOrder;    // 1 = first-choice penalty taker
    private Double form;
    private Double selectedByPercent;

    public PlayerEntity() {}

    public PlayerEntity(Integer id, String webName, Integer teamId, Integer positionId, Double price, Integer totalPoints) {
        this.id = id;
        this.webName = webName;
        this.teamId = teamId;
        this.positionId = positionId;
        this.price = price;
        this.totalPoints = totalPoints;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getWebName() { return webName; }
    public void setWebName(String webName) { this.webName = webName; }

    public Integer getTeamId() { return teamId; }
    public void setTeamId(Integer teamId) { this.teamId = teamId; }

    public Integer getPositionId() { return positionId; }
    public void setPositionId(Integer positionId) { this.positionId = positionId; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Integer getTotalPoints() { return totalPoints; }
    public void setTotalPoints(Integer totalPoints) { this.totalPoints = totalPoints; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getChanceOfPlaying() { return chanceOfPlaying; }
    public void setChanceOfPlaying(Integer chanceOfPlaying) { this.chanceOfPlaying = chanceOfPlaying; }

    public String getNews() { return news; }
    public void setNews(String news) { this.news = news; }

    public Integer getMinutes() { return minutes; }
    public void setMinutes(Integer minutes) { this.minutes = minutes; }

    public Integer getStarts() { return starts; }
    public void setStarts(Integer starts) { this.starts = starts; }

    public Integer getGoals() { return goals; }
    public void setGoals(Integer goals) { this.goals = goals; }

    public Integer getAssists() { return assists; }
    public void setAssists(Integer assists) { this.assists = assists; }

    public Double getExpectedGoals() { return expectedGoals; }
    public void setExpectedGoals(Double expectedGoals) { this.expectedGoals = expectedGoals; }

    public Double getExpectedAssists() { return expectedAssists; }
    public void setExpectedAssists(Double expectedAssists) { this.expectedAssists = expectedAssists; }

    public Double getExpectedGoalsConceded() { return expectedGoalsConceded; }
    public void setExpectedGoalsConceded(Double expectedGoalsConceded) { this.expectedGoalsConceded = expectedGoalsConceded; }

    public Integer getSaves() { return saves; }
    public void setSaves(Integer saves) { this.saves = saves; }

    public Integer getBonus() { return bonus; }
    public void setBonus(Integer bonus) { this.bonus = bonus; }

    public Integer getYellowCards() { return yellowCards; }
    public void setYellowCards(Integer yellowCards) { this.yellowCards = yellowCards; }

    public Integer getPenaltiesOrder() { return penaltiesOrder; }
    public void setPenaltiesOrder(Integer penaltiesOrder) { this.penaltiesOrder = penaltiesOrder; }

    public Double getForm() { return form; }
    public void setForm(Double form) { this.form = form; }

    public Double getSelectedByPercent() { return selectedByPercent; }
    public void setSelectedByPercent(Double selectedByPercent) { this.selectedByPercent = selectedByPercent; }
}
