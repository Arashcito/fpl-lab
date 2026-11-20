package com.arash.fpllab.service;

import java.util.*;

import com.arash.fpllab.dto.PlayerDbView;
import com.arash.fpllab.dto.PlayerView;
import com.arash.fpllab.client.FplClient;
import com.arash.fpllab.entity.PlayerEntity;
import com.arash.fpllab.entity.TeamEntity;
import com.arash.fpllab.model.Gameweek;
import com.arash.fpllab.model.Player;
import com.arash.fpllab.model.Position;
import com.arash.fpllab.model.Team;
import com.arash.fpllab.repository.PlayerRepository;
import com.arash.fpllab.repository.TeamRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ResponseStatusException;


@Service
public class FplService {

    private final FplClient fplClient;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;



    public FplService(FplClient fplClient, TeamRepository teamRepository, PlayerRepository playerRepository) {
        this.fplClient = fplClient;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
    }

    public JsonNode bootstrap() {
        return fplClient.getBootstrapStatic();
    }

    public List<Team> getTeams() {
        JsonNode root = fplClient.getBootstrapStatic();
        JsonNode teamsNode = root.get("teams");

        List<Team> teams = new ArrayList<>();

        for (JsonNode team : teamsNode) {
            teams.add(
                    new Team(
                            team.get("id").asInt(),
                            team.get("name").asText()
                    )
            );


        }

        return teams;
    }


    public List<Player> getPlayers(Integer teamId, Integer positionId) {
        JsonNode root = fplClient.getBootstrapStatic();
        JsonNode elements = root.get("elements");

        List<Player> players = new ArrayList<>();

        for (JsonNode p : elements) {
            int tId = p.get("team").asInt();
            int posId = p.get("element_type").asInt();

            if (teamId != null && tId != teamId) {
                continue;
            }
            if (positionId != null && posId != positionId) {
                continue;
            }

            players.add(new Player(
                    p.get("id").asInt(),
                    p.get("web_name").asText(),
                    tId,
                    posId,
                    p.get("now_cost").asInt() / 10.0,
                    p.get("total_points").asInt()
            ));
        }

        return players;
    }

    public List<Gameweek> getGameweeks() {
        var root = bootstrap();
        var events = root.get("events");

        List<Gameweek> result = new ArrayList<>();
        for (var e : events) {
            result.add(new Gameweek(
                    e.get("id").asInt(),
                    e.get("name").asText(),
                    e.get("deadline_time").asText(),
                    e.get("is_current").asBoolean(),
                    e.get("is_next").asBoolean()
            ));
        }
        return result;
    }


    public List<Position> getPositions() {
        var root = bootstrap();
        var types = root.get("element_types");

        List<Position> result = new ArrayList<>();
        for (var t : types) {
            result.add(new Position(
                    t.get("id").asInt(),
                    t.get("singular_name").asText(),
                    t.get("singular_name_short").asText()
            ));

        }
        return result;
    }


    public Player getPlayerById(int playerId) {
        var root = bootstrap();
        var elements = root.get("elements");

        for (var p : elements) {
            if (p.get("id").asInt() == playerId) {
                return new Player(
                        p.get("id").asInt(),
                        p.get("web_name").asText(),
                        p.get("team").asInt(),
                        p.get("element_type").asInt(),
                        p.get("now_cost").asInt() / 10.0,
                        p.get("total_points").asInt()
                );
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found: " + playerId);

    }

    public List<Player> searchPlayers(String q) {
        String query = q == null ? "" : q.toLowerCase(Locale.ROOT);

        var root = bootstrap();
        var elements = root.get("elements");

        List<Player> results= new ArrayList<>();

        for (var p : elements) {
            String name = p.get("web_name").asText().toLowerCase(Locale.ROOT);
            if (!name.contains(query)) {
                continue;
            }

            results.add(new Player(
                    p.get("id").asInt(),
                    p.get("web_name").asText(),
                    p.get("team").asInt(),
                    p.get("element_type").asInt(),
                    p.get("now_cost").asInt() / 10.0,
                    p.get("total_points").asInt()
            ));
        }
        return results;
    }


    public List<Player> getPlayersSorted(int limit) {
        List<Player> all = getPlayers(null, null);

        all.sort(Comparator.comparingInt(Player::getTotalPoints).reversed());

        if (limit <= 0) limit = 20;
        if (limit > all.size()) limit = all.size();
        return all.subList(0, limit);
    }

    public List<Player> getPlayersSorted(Integer teamId, Integer positionId, String sortBy,
                                         String order, Integer limit) {
        List<Player> players = getPlayers(teamId, positionId);

        Comparator<Player> comparator;

        if ("price".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparingDouble(Player::getPrice);
        } else if ("name".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(Player::getWebName, String.CASE_INSENSITIVE_ORDER);
        } else { // default = points
            comparator = Comparator.comparingInt(Player::getTotalPoints);
        }

        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }

        players.sort(comparator);

        if (limit != null) {
            int l = Math.max(1, limit);
            if (l < players.size()) {
                return players.subList(0, l);
            }
        }
        return players;
    }

    private Map<Integer, String> teamIdToName() {
        var root = bootstrap();
        var teams = root.get("teams");

        Map<Integer, String> map = new HashMap<>();
        for (var t : teams) {
            map.put(t.get("id").asInt(), t.get("name").asText());
        }
        return map;
    }

    private Map<Integer, String> positionIdToName() {
        var root = bootstrap();
        var types = root.get("element_types");

        Map<Integer, String> map = new HashMap<>();
        for (var p : types) {
            map.put(p.get("id").asInt(), p.get("singular_name").asText());
        }
        return map;
    }

    public List<PlayerView> getPlayerViews(Integer teamId, Integer positionId,
                                           String sortBy, String order, Integer limit) {

        Map<Integer, String> teamNames = teamIdToName();
        Map<Integer, String> posNames = positionIdToName();

        // reuse your sorted players
        List<Player> players = getPlayersSorted(teamId, positionId, sortBy, order, limit);

        List<PlayerView> result = new ArrayList<>();
        for (Player p : players) {
            result.add(new PlayerView(
                    p.getId(),
                    p.getWebName(),
                    p.getTeamId(),
                    teamNames.getOrDefault(p.getTeamId(), "Unknown"),
                    p.getPositionId(),
                    posNames.getOrDefault(p.getPositionId(), "Unknown"),
                    p.getPrice(),
                    p.getTotalPoints()
            ));
        }
        return result;
    }

    public List<TeamEntity> getTeamsFromDb() {
        return teamRepository.findAll();
    }

    public List<PlayerEntity> getPlayersFromDb() {
        return playerRepository.findAll();
    }

    private String positionName(int positionId) {
        return switch (positionId) {
            case 1 -> "Goalkeeper";
            case 2 -> "Defender";
            case 3 -> "Midfielder";
            case 4 -> "Forward";
            default -> "Unknown";
        };
    }

    public List<PlayerDbView> getPlayerViewsFromDb(Integer limit) {

        // teamId -> teamName
        Map<Integer, String> teamNames = new HashMap<>();
        for (var t : teamRepository.findAll()) {
            teamNames.put(t.getId(), t.getName());
        }

        var players = playerRepository.findAll();

        int max = (limit == null || limit <= 0) ? 50 : limit;

        List<PlayerDbView> result = new ArrayList<>();
        for (int i = 0; i < players.size() && i < max; i++) {
            var p = players.get(i);

            result.add(new PlayerDbView(
                    p.getId(),
                    p.getWebName(),
                    teamNames.getOrDefault(p.getTeamId(), "Unknown"),
                    positionName(p.getPositionId()),
                    p.getPrice(),
                    p.getTotalPoints()
            ));
        }

        return result;
    }


    public List<PlayerEntity> queryPlayersFromDb(Integer teamId, Integer positionId,
                                                 String sortBy, String order, Integer limit) {
        int size = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);

        String sortField = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "price" -> "price";
            case "name" -> "webName";
            default -> "totalPoints";
        };

        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;

        var pageable = PageRequest.of(0, size, Sort.by(direction, sortField));

        if (teamId != null && positionId != null) {
            return playerRepository.findByTeamIdAndPositionId(teamId, positionId, pageable);
        }
        if (teamId != null) {
            return playerRepository.findByTeamId(teamId, pageable);
        }
        if (positionId != null) {
            return playerRepository.findByPositionId(positionId, pageable);
        }
        return playerRepository.findAll(pageable).getContent();
    }

    public List<PlayerDbView> queryPlayerViewsFromDb(Integer teamId, Integer positionId,
                                                     String sortBy, String order, Integer limit) {

        // 1) Get filtered/sorted players from DB
        List<PlayerEntity> players = queryPlayersFromDb(teamId, positionId, sortBy, order, limit);

        // 2) Build teamId -> teamName map once
        Map<Integer, String> teamNames = new HashMap<>();
        for (var t : teamRepository.findAll()) {
            teamNames.put(t.getId(), t.getName());
        }

        // 3) Convert entities -> views
        List<PlayerDbView> result = new ArrayList<>();
        for (var p : players) {
            result.add(new PlayerDbView(
                    p.getId(),
                    p.getWebName(),
                    teamNames.getOrDefault(p.getTeamId(), "Unknown"),
                    positionName(p.getPositionId()),
                    p.getPrice(),
                    p.getTotalPoints()
            ));
        }

        return result;
    }


    public List<PlayerDbView> searchPlayerViewsFromDb(String q, Integer limit) {

        int size = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        var pageable = PageRequest.of(0, size);

        // DB search
        List<PlayerEntity> players = playerRepository.searchByWebName(q == null ? "" : q, pageable);

        // team map
        Map<Integer, String> teamNames = new HashMap<>();
        for (var t : teamRepository.findAll()) {
            teamNames.put(t.getId(), t.getName());
        }

        // convert
        List<PlayerDbView> result = new ArrayList<>();
        for (var p : players) {
            result.add(new PlayerDbView(
                    p.getId(),
                    p.getWebName(),
                    teamNames.getOrDefault(p.getTeamId(), "Unknown"),
                    positionName(p.getPositionId()),
                    p.getPrice(),
                    p.getTotalPoints()
            ));
        }

        return result;
    }

}
