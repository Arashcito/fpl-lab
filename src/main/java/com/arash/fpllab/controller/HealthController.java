package com.arash.fpllab.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeatlhController {
    @GetMapping("/api/health")
    public String health() {
        return "FPL is running i guess";
    }
}
