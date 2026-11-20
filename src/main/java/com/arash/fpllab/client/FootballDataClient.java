package com.arash.fpllab.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Live Premier League table from football-data.org:
 * https://api.football-data.org/v4/competitions/PL/standings
 * Needs a free API key (https://www.football-data.org/client/register),
 * set through the FOOTBALL_DATA_API_KEY environment variable.
 */
@Component
public class FootballDataClient {

    private static final String AUTH_HEADER = "X-Auth-Token";

    private final RestClient restClient;
    private final String apiKey;
    private final String competitionCode;

    public FootballDataClient(@Value("${football-data.base-url}") String baseUrl,
                              @Value("${football-data.api-key:}") String apiKey,
                              @Value("${football-data.competition:PL}") String competitionCode) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.competitionCode = competitionCode;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // Cached because the free tier allows only 10 requests per minute.
    @Cacheable("liveStandings")
    public JsonNode getStandings() {
        return restClient.get()
                .uri("/competitions/{code}/standings", competitionCode)
                .header(AUTH_HEADER, apiKey)
                .retrieve()
                .body(JsonNode.class);
    }
}
