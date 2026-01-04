package com.arash.fpllab.repository;

import com.arash.fpllab.entity.PlayerEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<PlayerEntity, Integer> {

    List<PlayerEntity> findByTeamId(Integer teamId, Pageable pageable);

    List<PlayerEntity> findByPositionId(Integer positionId, Pageable pageable);

    List<PlayerEntity> findByTeamIdAndPositionId(Integer teamId, Integer positionId, Pageable pageable);
}
