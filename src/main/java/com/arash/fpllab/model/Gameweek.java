package com.arash.fpllab.model;

public class Gameweek {
    private int id;
    private String name;
    private String deadlineTime;
    private boolean isCurrent;
    private boolean isNext;

    public Gameweek(int id, String name, String deadlineTime, boolean isCurrent, boolean isNext) {
        this.id = id;
        this.name = name;
        this.deadlineTime = deadlineTime;
        this.isCurrent = isCurrent;
        this.isNext = isNext;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }


    public String getDeadlineTime() {
        return deadlineTime;
    }
    public boolean isNext() {
        return isNext;
    }

    public boolean isCurrent() {
        return isCurrent;
    }



}
