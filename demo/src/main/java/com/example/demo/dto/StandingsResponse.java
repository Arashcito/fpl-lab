package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StandingsResponse(
        CompetitionInfo competition,
        SeasonInfo season,
        List<StandingGroup> standings
) {}
