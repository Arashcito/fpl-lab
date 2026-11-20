package com.arash.fpllab.prediction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MyTeamServiceTest {

    @Test
    void sellingPriceKeepsHalfOfTheRise() {
        assertEquals(50, MyTeamService.sellingPrice(50, 50));
        assertEquals(51, MyTeamService.sellingPrice(50, 52));   // +0.2 -> keep +0.1
        assertEquals(51, MyTeamService.sellingPrice(50, 53));   // +0.3 -> keep +0.1 (rounded down)
        assertEquals(48, MyTeamService.sellingPrice(50, 48));   // falls are taken in full
    }

    @Test
    void freeTransfersRollAndChipsDontUseThem() throws Exception {
        var history = new ObjectMapper().readTree("""
                {
                  "current": [
                    {"event": 1, "event_transfers": 0},
                    {"event": 2, "event_transfers": 0},
                    {"event": 3, "event_transfers": 9},
                    {"event": 4, "event_transfers": 0},
                    {"event": 5, "event_transfers": 2},
                    {"event": 6, "event_transfers": 3}
                  ],
                  "chips": [{"name": "wildcard", "event": 3}]
                }
                """);
        // GW2: 1 -> 2, GW3 wildcard: 2 -> 3, GW4: 3 -> 4, GW5 uses 2: 2 -> 3, GW6 uses 3: 0 -> 1
        assertEquals(1, MyTeamService.estimateFreeTransfers(history, 1, 6));
        // after GW4 the bank would be 4
        assertEquals(4, MyTeamService.estimateFreeTransfers(history, 1, 4));
    }

    @Test
    void freeTransfersCapAtFive() throws Exception {
        var history = new ObjectMapper().readTree("{\"current\": [], \"chips\": []}");
        assertEquals(5, MyTeamService.estimateFreeTransfers(history, 1, 10));
    }
}
