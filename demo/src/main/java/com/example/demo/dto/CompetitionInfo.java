package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitionInfo(
        int id,
        String name,
        String code,
        String type,
        String emblem
) {}
