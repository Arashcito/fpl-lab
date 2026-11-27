const pickFilters = ["position", "horizon", "max-price"].map(id => document.getElementById(id));
const picksContainer = document.getElementById("picks");

async function loadPicks() {
    picksContainer.innerHTML = loadingState("Ranking the player pool");
    try {
        const [position, horizon, maxPrice] = pickFilters.map(element => element.value);
        // the server filters by price and returns only the 40 rows shown (keeps the download small)
        const players = await fetchProjections(horizon, position, 40, maxPrice);
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
