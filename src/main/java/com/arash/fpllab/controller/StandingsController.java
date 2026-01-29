package com.arash.fpllab.controller;

import com.arash.fpllab.model.Standing;
import com.arash.fpllab.service.StandingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StandingsController {

    private final StandingsService standingsService;

    public StandingsController(StandingsService standingsService) {
        this.standingsService = standingsService;
    }

    @GetMapping("api/standings")
    public List<Standing> getStandings() {
        return standingsService.getStandings();
    }
}
