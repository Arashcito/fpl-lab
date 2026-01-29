package com.arash.fpllab.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FplClient {

    private final RestClient restClient;

    public FplClient(@Value("${fpl.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .messageConverters(converters -> {
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }

    @Cacheable("bootstrap")
    public JsonNode getBootstrapStatic() {
        return restClient.get()
                .uri("/bootstrap-static/")
                .retrieve()
                .body(JsonNode.class);
    }

}