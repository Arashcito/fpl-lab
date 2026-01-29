package com.example.demo.client;

import com.example.demo.dto.StandingsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class FootballDataClient {

    private static final String AUTH_HEADER = "X-Auth-Token";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String plCode;

    public FootballDataClient(
            RestTemplate restTemplate,
            @Value("${football-data.base-url}") String baseUrl,
            @Value("${football-data.api-key}") String apiKey,
            @Value("${football-data.pl-code}") String plCode) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.plCode = plCode;
    }

    /**
     * Fetches Premier League standings from football-data.org.
     * Returns the full response; use the "TOTAL" standing for the main table.
     */
    public StandingsResponse getPremierLeagueStandings() {
        String url = baseUrl + "/competitions/" + plCode + "/standings";
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<StandingsResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                StandingsResponse.class);

        return response.getBody();
    }
}
