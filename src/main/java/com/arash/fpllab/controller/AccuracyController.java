package com.arash.fpllab.controller;

import com.arash.fpllab.prediction.AccuracyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

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

    // Download the predictions saved for a gameweek (JSON in Neon Object Storage, via a 15-minute signed link).
    // example: http://localhost:8080/api/accuracy/12/download
    @GetMapping("/api/accuracy/{event}/download")
    public RedirectView download(@PathVariable int event) {
        String link = accuracyService.predictionsDownloadLink(event)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No archived predictions for GW" + event + " (object storage not configured, or nothing saved yet)"));
        return new RedirectView(link);
    }
}
