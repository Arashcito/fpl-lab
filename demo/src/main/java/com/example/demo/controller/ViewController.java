package com.example.demo.controller;

import com.example.demo.dto.StandingGroup;
import com.example.demo.dto.StandingsResponse;
import com.example.demo.service.StandingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
public class ViewController {

    private final StandingsService standingsService;

    public ViewController(StandingsService standingsService) {
        this.standingsService = standingsService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/standings")
    public String standings(Model model) {
        StandingsResponse response = standingsService.getPremierLeagueStandings();
        Optional<StandingGroup> table = standingsService.getPremierLeagueTable();

        model.addAttribute("response", response);
        model.addAttribute("table", table.orElse(null));
        return "standings";
    }
}
