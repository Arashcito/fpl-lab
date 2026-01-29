package com.arash.fpllab.model;

public class Player {
    private int id;
    private String webName;
    private int teamId;
    private int positionId;
    private double price;
    private int totalPoints;

    public Player(int id, String webName, int teamId, int positionId, double price, int totalPoints) {
        this.id = id;
        this.webName = webName;
        this.teamId = teamId;
        this.positionId = positionId;
        this.price = price;
        this.totalPoints = totalPoints;
    }

    public int getId() {
        return id;
    }

    public String getWebName() {
        return webName;
    }

    public int getTeamId() {
        return teamId;
    }

    public int getPositionId() {
        return positionId;
    }

    public double getPrice() {
        return price;
    }

    public int getTotalPoints() {
        return totalPoints;
    }
}
