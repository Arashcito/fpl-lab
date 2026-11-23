package com.arash.fpllab.controller;

import com.arash.fpllab.prediction.AccuracyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccuracyController {

    private final AccuracyService accuracyService;

    public AccuracyController(AccuracyService accuracyService) {
        this.accuracyService = accuracyService;
    }

    // Predicted vs. actual points for every tracked gameweek, ours vs. FPL's own estimate.
    // example: http://localhost:8080/api/accuracy
    @GetMapping("/api/accuracy")
    public AccuracyService.Report report() {
        return accuracyService.report();
    }

    // Top predictions and biggest misses for one gameweek.
    // example: http://localhost:8080/api/accuracy/11
    @GetMapping("/api/accuracy/{event}")
    public AccuracyService.GameweekDetail detail(@PathVariable int event) {
        return accuracyService.detail(event);
    }
}
