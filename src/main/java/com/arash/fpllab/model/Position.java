package com.arash.fpllab.model;

public class Position {
    private int id;
    private String name;
    private String shortName;

    public Position(int id, String name, String shortName) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }
}
