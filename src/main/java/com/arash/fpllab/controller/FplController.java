package com.arash.fpllab.controller;

import com.arash.fpllab.service.FplService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;



@RestController
public class FplController {

    private final FplService fplService;

    public FplController(FplService fplService) {
        this.fplService = fplService;
    }

    @GetMapping("/api/fpl/bootstrap")
    public JsonNode bootstrap() {
        return fplService.bootstrap();
    }

}
