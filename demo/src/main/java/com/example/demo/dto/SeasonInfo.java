package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeasonInfo(
        int id,
        String startDate,
        String endDate,
        Integer currentMatchday
) {}
