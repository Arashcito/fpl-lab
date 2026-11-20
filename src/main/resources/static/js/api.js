// Relative paths so the frontend works on any host/port (local, Docker, deployed)
const API_BASE = "/api/fpl";

async function fetchJson(url) {
    const res = await fetch(url);
    if (!res.ok) {
        throw new Error(`Request failed: ${res.status} ${url}`);
    }
    return await res.json();
}

async function fetchPlayers() {
    return fetchJson(`${API_BASE}/players`);
}

async function fetchStandings() {
    return fetchJson("/api/standings");
}

async function fetchProjections(horizon = 3, position = "", limit = 50) {
    const params = new URLSearchParams({ horizon, limit });
    if (position) params.set("position", position);
    return fetchJson(`/api/projections?${params}`);
}

async function fetchMyTeam(entryId, horizon = 3, freeTransfers = "") {
    const params = new URLSearchParams({ horizon });
    if (freeTransfers !== "") params.set("freeTransfers", freeTransfers);
    const res = await fetch(`/api/my-team/${encodeURIComponent(entryId)}?${params}`);
    if (!res.ok) {
        let message = `Request failed (${res.status})`;
        try {
            const body = await res.json();
            if (body.message) message = body.message;
        } catch (e) { /* not JSON */ }
        if (res.status === 404) message = "No FPL team found with that ID.";
        throw new Error(message);
    }
    return res.json();
}
