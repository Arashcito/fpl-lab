package com.arash.fpllab.repository;

import com.arash.fpllab.entity.PlayerGameweekEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface PlayerGameweekRepository extends JpaRepository<PlayerGameweekEntity, Long> {

    @Query("SELECT DISTINCT g.event FROM PlayerGameweekEntity g")
    Set<Integer> findSyncedEvents();

    List<PlayerGameweekEntity> findByEventGreaterThanEqual(Integer event);
}
