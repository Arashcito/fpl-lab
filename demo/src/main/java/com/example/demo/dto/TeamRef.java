package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamRef(
        int id,
        String name,
        String shortName,
        String tla,
        String crest
) {}
