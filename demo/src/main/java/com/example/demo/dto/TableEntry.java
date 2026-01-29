package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TableEntry(
        int position,
        TeamRef team,
        int playedGames,
        String form,
        int won,
        int draw,
        int lost,
        int points,
        int goalsFor,
        int goalsAgainst,
        int goalDifference
) {}
