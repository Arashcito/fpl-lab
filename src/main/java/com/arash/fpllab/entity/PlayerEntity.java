package com.arash.fpllab.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    public PlayerEntity() {}

    public PlayerEntity(Integer id, String webName, Integer teamId, Integer positionId, Double price, Integer totalPoints) {
        this.id = id;
        this.webName = webName;
        this.teamId = teamId;
        this.positionId = positionId;
        this.price = price;
        this.totalPoints = totalPoints;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getPositionId() {
        return positionId;
    }

    public void setPositionId(Integer positionId) {
        this.positionId = positionId;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public String getWebName() {
        return webName;
    }

    public void setWebName(String webName) {
        this.webName = webName;
    }
}
