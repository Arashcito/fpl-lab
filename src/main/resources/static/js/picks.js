const pickFilters = ["position", "horizon", "max-price"].map(id => document.getElementById(id));
const picksContainer = document.getElementById("picks");

async function loadPicks() {
    picksContainer.innerHTML = loadingState("Ranking the player pool");
    try {
        const [position, horizon, maxPrice] = pickFilters.map(element => element.value);
        let players = await fetchProjections(horizon, position, 400);
        if (maxPrice) players = players.filter(player => player.price <= Number(maxPrice));
        players = players.slice(0, 40);
        if (!players.length) {
            picksContainer.innerHTML = emptyState("No matching players", "Try another position or raise the maximum price.");
            return;
        }
        picksContainer.innerHTML = projectionTable(players, { showOwnership: true });
        attachBreakdownToggles(picksContainer);
    } catch (error) {
        console.error(error);
        picksContainer.innerHTML = errorState("Couldn’t load the projections", "The server may still be waking up. Wait a moment and try again.", "picks");
        picksContainer.querySelector("[data-retry]")?.addEventListener("click", loadPicks);
    }
}

pickFilters.forEach(element => element.addEventListener("change", loadPicks));
document.getElementById("picks-form").addEventListener("submit", event => { event.preventDefault(); loadPicks(); });
loadPicks();
