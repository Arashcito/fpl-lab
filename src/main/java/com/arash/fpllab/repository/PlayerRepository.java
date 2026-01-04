package com.arash.fpllab.repository;

import com.arash.fpllab.entity.PlayerEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerRepository extends JpaRepository<PlayerEntity, Integer> {

    List<PlayerEntity> findByTeamId(Integer teamId, Pageable pageable);

    List<PlayerEntity> findByPositionId(Integer positionId, Pageable pageable);

    List<PlayerEntity> findByTeamIdAndPositionId(Integer teamId, Integer positionId, Pageable pageable);

    @Query("""
       SELECT p FROM PlayerEntity p
       WHERE LOWER(p.webName) LIKE LOWER(CONCAT('%', :q, '%'))
       """)
    List<PlayerEntity> searchByWebName(@Param("q") String q, org.springframework.data.domain.Pageable pageable);

}
