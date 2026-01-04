const API_BASE = "http://localhost:8080/api";

async function fetchPlayers() {
    const res = await fetch(`${API_BASE}/players`);
    return await res.json();
}
