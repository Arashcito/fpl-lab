package com.arash.fpllab.model;

public class Player {
    private int id;
    private String webName;
    private int teamId;
    private int positionId;
    private int nowCost;
    private int totalPoints;

    public Player(int id, String webName, int teamId, int positionId, int nowCost, int totalPoints) {
        this.id = id;
        this.webName = webName;
        this.teamId = teamId;
        this.nowCost = nowCost;
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

    public int getNowCost() {
        return nowCost;
    }

    public int getTotalPoints() {
        return totalPoints;
    }
}
