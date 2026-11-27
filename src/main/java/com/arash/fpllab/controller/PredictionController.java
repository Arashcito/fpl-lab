package com.arash.fpllab.controller;

import com.arash.fpllab.prediction.MyTeamService;
import com.arash.fpllab.prediction.PredictionService;
import com.arash.fpllab.prediction.Projection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PredictionController {

    private final PredictionService predictionService;
    private final MyTeamService myTeamService;

    public PredictionController(PredictionService predictionService, MyTeamService myTeamService) {
        this.predictionService = predictionService;
        this.myTeamService = myTeamService;
    }

    // Expected points for the next few gameweeks, best first.
    // example: http://localhost:8080/api/projections?horizon=3&position=MID&maxPrice=7.5&limit=20
    @GetMapping("/api/projections")
    public List<Projection.Player> projections(@RequestParam(defaultValue = "3") int horizon,
                                               @RequestParam(required = false) String position,
                                               @RequestParam(required = false) Double maxPrice,
                                               @RequestParam(defaultValue = "50") int limit) {
        return predictionService.top(horizon, position, maxPrice, limit);
    }

    // Transfer and captain advice for one FPL team (the number in your FPL "Points" page URL).
    // example: http://localhost:8080/api/my-team/1?horizon=3
    @GetMapping("/api/my-team/{entryId}")
    public MyTeamService.Response myTeam(@PathVariable int entryId,
                                         @RequestParam(defaultValue = "3") int horizon,
                                         @RequestParam(required = false) Integer freeTransfers) {
        return myTeamService.analyse(entryId, horizon, freeTransfers);
    }
}
