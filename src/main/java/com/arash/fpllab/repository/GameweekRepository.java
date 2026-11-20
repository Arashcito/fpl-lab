package com.arash.fpllab.repository;

import com.arash.fpllab.entity.GameweekEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameweekRepository extends JpaRepository<GameweekEntity, Integer> {

    Optional<GameweekEntity> findFirstByNextTrue();

    Optional<GameweekEntity> findFirstByCurrentTrue();
}
