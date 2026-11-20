package com.arash.fpllab.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client for the public FPL API (https://fantasy.premierleague.com/api).
 */
@Component
public class FplClient {

    private final RestClient restClient;

    public FplClient(@Value("${fpl.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "fpl-lab")
                .messageConverters(converters -> {
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }

    // Players, teams, gameweeks and positions with season-to-date stats.
    @Cacheable("bootstrap")
    public JsonNode getBootstrapStatic() {
        return get("/bootstrap-static/");
    }

    // All season fixtures with scores; used to build the league table.
    @Cacheable("fixtures")
    public JsonNode getFixtures() {
        return get("/fixtures/");
    }

    // Every player's stats for one gameweek.
    public JsonNode getEventLive(int event) {
        return get("/event/" + event + "/live/");
    }

    // A manager's FPL team: name, bank at last deadline, current gameweek.
    public JsonNode getEntry(int entryId) {
        return get("/entry/" + entryId + "/");
    }

    // The 15 players a manager picked for a gameweek.
    public JsonNode getEntryPicks(int entryId, int event) {
        return get("/entry/" + entryId + "/event/" + event + "/picks/");
    }

    // Every transfer a manager has made this season (with buy/sell prices).
    public JsonNode getEntryTransfers(int entryId) {
        return get("/entry/" + entryId + "/transfers/");
    }

    // Per-gameweek history of a manager's team, including chips played.
    public JsonNode getEntryHistory(int entryId) {
        return get("/entry/" + entryId + "/history/");
    }

    // One player's match-by-match history, including their price each gameweek.
    public JsonNode getElementSummary(int playerId) {
        return get("/element-summary/" + playerId + "/");
    }

    private JsonNode get(String path) {
        return restClient.get()
                .uri(path)
                .retrieve()
                .body(JsonNode.class);
    }
}
