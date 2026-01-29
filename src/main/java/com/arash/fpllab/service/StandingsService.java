package com.arash.fpllab.service;

import com.arash.fpllab.model.Standing;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StandingsService {

    public List<Standing> getStandings() {
        return List.of(
                new Standing("Arsenal", 41),
                new Standing("Man City", 39),
                new Standing("Liverpool", 40)
        );
    }


}
