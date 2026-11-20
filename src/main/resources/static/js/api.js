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
