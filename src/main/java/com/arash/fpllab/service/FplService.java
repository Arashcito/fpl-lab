package com.arash.fpllab.service;

import com.arash.fpllab.client.FplClient;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;


@Service
public class FplService {

    private final FplClient fplClient;

    public FplService(FplClient fplClient) {
        this.fplClient = fplClient;
    }

    public JsonNode bootstrap() {
        return fplClient.getBootstrapStatic();
    }

}
