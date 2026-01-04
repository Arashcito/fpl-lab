package com.arash.fpllab.dto;

public class PlayerDbView {
    private Integer id;
    private String webName;
    private String teamName;
    private String positionName;
    private Double price;
    private Integer totalPoints;

    public PlayerDbView(Integer id, String webName, String teamName, String positionName, Double price, Integer totalPoints) {
        this.id = id;
        this.webName = webName;
        this.teamName = teamName;
        this.positionName = positionName;
        this.price = price;
        this.totalPoints = totalPoints;
    }

    public Integer getId() { return id; }
    public String getWebName() { return webName; }
    public String getTeamName() { return teamName; }
    public String getPositionName() { return positionName; }
    public Double getPrice() { return price; }
    public Integer getTotalPoints() { return totalPoints; }
}
