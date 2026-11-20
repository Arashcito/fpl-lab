package com.arash.fpllab.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "gameweeks")
public class GameweekEntity {

    @Id
    private Integer id;

    private String name;
    private Instant deadlineTime;
    private Boolean finished;
    private Boolean current;
    private Boolean next;

    public GameweekEntity() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getDeadlineTime() { return deadlineTime; }
    public void setDeadlineTime(Instant deadlineTime) { this.deadlineTime = deadlineTime; }

    public Boolean getFinished() { return finished; }
    public void setFinished(Boolean finished) { this.finished = finished; }

    public Boolean getCurrent() { return current; }
    public void setCurrent(Boolean current) { this.current = current; }

    public Boolean getNext() { return next; }
    public void setNext(Boolean next) { this.next = next; }
}
