package com.arash.fpllab.repository;

import com.arash.fpllab.entity.PredictionSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PredictionSnapshotRepository extends JpaRepository<PredictionSnapshotEntity, String> {

    List<PredictionSnapshotEntity> findByEvent(Integer event);
}
