package com.arash.fpllab.prediction;

import com.arash.fpllab.client.FplClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Loads a manager's FPL team by ID and advises transfers and captaincy.
 */
@Service
public class MyTeamService {

    private final FplClient fplClient;
    private final PredictionService predictionService;

    public MyTeamService(FplClient fplClient, PredictionService predictionService) {
        this.fplClient = fplClient;
        this.predictionService = predictionService;
    }

    // ---- response records ----

    public record SquadPlayer(Projection.Player player, double sellingPrice, boolean starter,
                              boolean captain, boolean viceCaptain) {}

    public record TransferView(Projection.Player out, Projection.Player in, double sellingPrice, double buyPrice) {}

    public record PlanView(List<TransferView> transfers, double gain, int hits, double netGain) {}

    public record Advice(String summary, boolean rollTransfer, PlanView recommended, PlanView bestSingle,
                         PlanView bestDouble, List<PlanView> alternatives) {}

    public record CaptainOption(Projection.Player player, double points) {}

    public record Response(int entryId, String teamName, String managerName, Integer overallRank,
                           Integer overallPoints, int nextEvent, List<Integer> events, int horizon,
                           double bank, int freeTransfers, boolean freeTransfersEstimated,
                           double squadValue, List<SquadPlayer> squad, List<CaptainOption> captainOptions,
                           Advice advice) {}

    // ---- main entry point ----

    // kept for 5 minutes (CacheScheduler) and cleared by every sync, so repeat visits are instant
    @Cacheable(value = "myTeam", key = "#entryId + '-' + #horizon + '-' + #freeTransfersOverride")
    public Response analyse(int entryId, int horizon, Integer freeTransfersOverride) {
        Projection.Set projections = predictionService.project(horizon);
        if (projections.events().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No upcoming gameweek found. The season may be over, or the data is still syncing.");
        }
        int h = projections.events().size();

        // the three requests don't depend on each other, so send them at the same time
        CompletableFuture<JsonNode> entryCall = async(() -> fetch(() -> fplClient.getEntry(entryId), "FPL team " + entryId + " not found"));
        CompletableFuture<JsonNode> transfersCall = async(() -> fetch(() -> fplClient.getEntryTransfers(entryId), "Could not load this team's transfers"));
        CompletableFuture<JsonNode> historyCall = async(() -> fetch(() -> fplClient.getEntryHistory(entryId), "Could not load this team's history"));
        // picks need the team's current gameweek; start with the game's current gameweek (almost always the same)
        int likelyEvent = currentGameweek();
        CompletableFuture<JsonNode> likelyPicksCall = likelyEvent > 0
                ? async(() -> fetch(() -> fplClient.getEntryPicks(entryId, likelyEvent), "Could not load this team's picks"))
                : null;
        JsonNode entry = await(entryCall);
        if (entry.path("current_event").isNull() || entry.path("current_event").isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This team has not played a gameweek yet.");
        }
        int currentEvent = entry.get("current_event").asInt();
        int startedEvent = entry.path("started_event").asInt(1);

        JsonNode picks = likelyPicksCall != null && likelyEvent == currentEvent
                ? await(likelyPicksCall)
                : fetch(() -> fplClient.getEntryPicks(entryId, currentEvent), "Could not load this team's picks");
        JsonNode transfers = await(transfersCall);
        JsonNode history = await(historyCall);

        // squad after any transfers already made for the next deadline
        Set<Integer> squadIds = new LinkedHashSet<>();
        for (JsonNode pick : picks.path("picks")) {
            squadIds.add(pick.get("element").asInt());
        }
        int bank = entry.path("last_deadline_bank").asInt(picks.path("entry_history").path("bank").asInt());
        int pendingTransfers = 0;
        List<JsonNode> pending = new ArrayList<>();
        for (JsonNode t : transfers) {
            if (t.path("event").asInt() > currentEvent) pending.add(t);
        }
        pending.sort(Comparator.comparing(t -> t.path("time").asText()));
        for (JsonNode t : pending) {
            squadIds.remove(t.get("element_out").asInt());
            squadIds.add(t.get("element_in").asInt());
            bank += t.get("element_out_cost").asInt() - t.get("element_in_cost").asInt();
            pendingTransfers++;
        }

        Map<Integer, Integer> purchasePrices = purchasePrices(transfers, squadIds, startedEvent);
        boolean ftEstimated = freeTransfersOverride == null;
        int freeTransfers = ftEstimated
                ? Math.max(0, estimateFreeTransfers(history, startedEvent, currentEvent) - pendingTransfers)
                : Math.max(0, Math.min(SquadOptimizer.MAX_FREE_TRANSFERS, freeTransfersOverride));

        // build optimiser inputs
        List<SquadOptimizer.Slot> squad = new ArrayList<>();
        Map<Integer, Integer> sellingPrices = new HashMap<>();
        for (int id : squadIds) {
            Projection.Player p = projections.players().get(id);
            if (p == null) continue;
            int now = (int) Math.round(p.price() * 10);
            int selling = sellingPrice(purchasePrices.getOrDefault(id, now), now);
            sellingPrices.put(id, selling);
            squad.add(slot(p, selling, h));
        }
        List<SquadOptimizer.Slot> pool = new ArrayList<>();
        for (Projection.Player p : projections.players().values()) {
            if (p.status() != null && (p.status().equals("u") || p.status().equals("n"))) continue;
            pool.add(slot(p, (int) Math.round(p.price() * 10), h));
        }

        SquadOptimizer.Result result = SquadOptimizer.optimise(squad, pool, bank, freeTransfers, h);
        SquadOptimizer.Lineup lineup = SquadOptimizer.bestLineup(squad, 0);

        Set<Integer> starters = new HashSet<>();
        lineup.starters().forEach(s -> starters.add(s.playerId()));
        List<SquadPlayer> squadView = new ArrayList<>();
        for (SquadOptimizer.Slot s : squad) {
            squadView.add(new SquadPlayer(projections.players().get(s.playerId()), s.price() / 10.0,
                    starters.contains(s.playerId()),
                    lineup.captain() != null && lineup.captain().playerId() == s.playerId(),
                    lineup.viceCaptain() != null && lineup.viceCaptain().playerId() == s.playerId()));
        }
        squadView.sort(Comparator.comparing((SquadPlayer sp) -> !sp.starter())
                .thenComparingInt(sp -> sp.player().positionId())
                .thenComparing(Comparator.comparingDouble((SquadPlayer sp) -> sp.player().total()).reversed()));

        List<CaptainOption> captains = lineup.starters().stream()
                .sorted(Comparator.comparingDouble((SquadOptimizer.Slot s) -> s.points()[0]).reversed())
                .limit(5)
                .map(s -> new CaptainOption(projections.players().get(s.playerId()), s.points()[0]))
                .toList();

        Advice advice = advice(result, projections, freeTransfers, h);
        return new Response(entryId, entry.path("name").asText(),
                (entry.path("player_first_name").asText() + " " + entry.path("player_last_name").asText()).trim(),
                entry.path("summary_overall_rank").isNull() ? null : entry.path("summary_overall_rank").asInt(),
                entry.path("summary_overall_points").asInt(),
                projections.firstEvent(), projections.events(), h, bank / 10.0, freeTransfers, ftEstimated,
                PredictionService.round2(result.currentValue()), squadView, captains, advice);
    }

    // ---- FPL rules ----

    /**
     * FPL selling price: you keep half of any price rise (rounded down to 0.1m), but take the full fall.
     *   selling = now <= bought ? now : bought + floor((now - bought) / 2)
     */
    static int sellingPrice(int purchase, int now) {
        return now <= purchase ? now : purchase + (now - purchase) / 2;
    }

    /**
     * Free transfers: 1 per gameweek after the first, unused ones roll over up to 5.
     * Gameweeks with a Wildcard or Free Hit don't use any.
     */
    static int estimateFreeTransfers(JsonNode history, int startedEvent, int currentEvent) {
        Set<Integer> chipWeeks = new HashSet<>();
        for (JsonNode chip : history.path("chips")) {
            String name = chip.path("name").asText();
            if (name.equals("wildcard") || name.equals("freehit")) chipWeeks.add(chip.path("event").asInt());
        }
        Map<Integer, Integer> transfersByEvent = new HashMap<>();
        for (JsonNode gw : history.path("current")) {
            transfersByEvent.put(gw.path("event").asInt(), gw.path("event_transfers").asInt());
        }
        int ft = 1;   // available for the gameweek after the team's first one
        for (int e = startedEvent + 1; e <= currentEvent; e++) {
            int used = chipWeeks.contains(e) ? 0 : transfersByEvent.getOrDefault(e, 0);
            ft = Math.min(SquadOptimizer.MAX_FREE_TRANSFERS, Math.max(0, ft - used) + 1);
        }
        return ft;
    }

    /** Price paid for each squad player: from the transfer that brought him in, or his season start price. */
    private Map<Integer, Integer> purchasePrices(JsonNode transfers, Set<Integer> squadIds, int startedEvent) {
        Map<Integer, Integer> prices = new HashMap<>();
        Map<Integer, String> latest = new HashMap<>();
        for (JsonNode t : transfers) {
            int in = t.get("element_in").asInt();
            String time = t.path("time").asText();
            if (squadIds.contains(in) && time.compareTo(latest.getOrDefault(in, "")) > 0) {
                latest.put(in, time);
                prices.put(in, t.get("element_in_cost").asInt());
            }
        }
        // players kept from the original squad: price when the team was created
        JsonNode elements = fplClient.getBootstrapStatic().path("elements");
        Map<Integer, JsonNode> byId = new HashMap<>();
        for (JsonNode e : elements) byId.put(e.get("id").asInt(), e);
        for (int id : squadIds) {
            if (prices.containsKey(id)) continue;
            JsonNode e = byId.get(id);
            if (e == null) continue;
            if (startedEvent <= 1) {
                prices.put(id, e.get("now_cost").asInt() - e.path("cost_change_start").asInt());
            } else {
                prices.put(id, priceAtEvent(id, startedEvent, e.get("now_cost").asInt()));
            }
        }
        return prices;
    }

    private int priceAtEvent(int playerId, int event, int fallback) {
        try {
            for (JsonNode h : fplClient.getElementSummary(playerId).path("history")) {
                if (h.path("round").asInt() >= event) return h.path("value").asInt(fallback);
            }
        } catch (Exception ignored) {
            // fall back to the current price
        }
        return fallback;
    }

    // ---- helpers ----

    private static SquadOptimizer.Slot slot(Projection.Player p, int price, int horizon) {
        double[] points = new double[horizon];
        for (int i = 0; i < horizon; i++) points[i] = p.pointsIn(i);
        return new SquadOptimizer.Slot(p.id(), p.positionId(), p.teamId(), price, points);
    }

    private static Advice advice(SquadOptimizer.Result r, Projection.Set projections, int freeTransfers, int horizon) {
        PlanView recommended = view(r.recommended(), projections);
        String summary;
        if (r.rollTransfer()) {
            double best = r.bestSingle() == null ? 0 : r.bestSingle().netGain();
            summary = freeTransfers == 0
                    ? "No transfer is worth a -4 hit right now. Hold."
                    : String.format(java.util.Locale.ROOT,
                    "Roll your transfer: the best move only gains %.1f pts over %d GWs (threshold %.1f).",
                    best, horizon, SquadOptimizer.ROLL_THRESHOLD);
        } else {
            List<String> moves = new ArrayList<>();
            for (TransferView t : recommended.transfers()) moves.add(t.out().name() + " -> " + t.in().name());
            summary = String.format(java.util.Locale.ROOT, "%s: +%.1f expected pts over %d GWs%s.",
                    String.join(", ", moves), r.recommended().netGain(), horizon,
                    r.recommended().hits() > 0 ? " (after a -" + (r.recommended().hits() * 4) + " hit)" : "");
        }
        return new Advice(summary, r.rollTransfer(), recommended, view(r.bestSingle(), projections),
                view(r.bestDouble(), projections),
                r.alternatives().stream().map(p -> view(p, projections)).toList());
    }

    private static PlanView view(SquadOptimizer.Plan plan, Projection.Set projections) {
        if (plan == null) return null;
        List<TransferView> transfers = plan.transfers().stream()
                .map(t -> new TransferView(projections.players().get(t.out().playerId()),
                        projections.players().get(t.in().playerId()), t.out().price() / 10.0, t.in().price() / 10.0))
                .toList();
        return new PlanView(transfers, PredictionService.round2(plan.gain()), plan.hits(),
                PredictionService.round2(plan.netGain()));
    }

    /** The game's current gameweek from the cached bootstrap data, or 0 if unknown. */
    private int currentGameweek() {
        for (JsonNode e : fplClient.getBootstrapStatic().path("events")) {
            if (e.path("is_current").asBoolean()) return e.path("id").asInt();
        }
        return 0;
    }

    private static final ExecutorService FPL_CALLS = Executors.newVirtualThreadPerTaskExecutor();

    private static CompletableFuture<JsonNode> async(Supplier<JsonNode> call) {
        return CompletableFuture.supplyAsync(call, FPL_CALLS);
    }

    /** Waits for a parallel call; errors (e.g. "team not found") come out exactly as if called directly. */
    private static JsonNode await(CompletableFuture<JsonNode> call) {
        try {
            return call.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    private interface Call {
        JsonNode get();
    }

    private static JsonNode fetch(Call call, String notFoundMessage) {
        try {
            JsonNode n = call.get();
            if (n == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from FPL");
            return n;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
    }
}
