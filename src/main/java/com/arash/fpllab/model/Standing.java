package com.arash.fpllab.model;

public class Standing {

    private String team;
    private int points;

    public Standing(String team, int points) {
        this.team = team;
        this.points = points;
    }

    public String getTeam() {
        return team;
    }

    public int getPoints() {
        return points;
    }
}
