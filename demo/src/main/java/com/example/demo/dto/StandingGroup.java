package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StandingGroup(
        String stage,
        String type,
        String group,
        List<TableEntry> table
) {}
