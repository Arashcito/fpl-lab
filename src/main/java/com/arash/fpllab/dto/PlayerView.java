package com.arash.fpllab.dto;

public class PlayerView {
    private int id;
    private String webName;
    private int teamId;
    private String teamName;
    private int positionId;
    private String positionName;
    private double price;
    private int totalPoints;

    public PlayerView(int id, String webName,
                      int teamId, String teamName,
                      int positionId, String positionName,
                      double price, int totalPoints) {
        this.id = id;
        this.webName = webName;
        this.teamId = teamId;
        this.teamName = teamName;
        this.positionId = positionId;
        this.positionName = positionName;
        this.price = price;
        this.totalPoints = totalPoints;
    }

    public int getId() { return id; }
    public String getWebName() { return webName; }
    public int getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public int getPositionId() { return positionId; }
    public String getPositionName() { return positionName; }
    public double getPrice() { return price; }
    public int getTotalPoints() { return totalPoints; }


}
