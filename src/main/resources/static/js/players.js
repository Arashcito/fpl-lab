const playerList = document.getElementById("players");
const playerSearch = document.getElementById("player-search");
const playerPosition = document.getElementById("player-position");
const playerSort = document.getElementById("player-sort");
let allPlayers = [];

async function loadPlayers() {
    playerList.innerHTML = `<li>${loadingState("Loading the player database")}</li>`;
    try {
        allPlayers = await fetchPlayers();
        if (!allPlayers?.length) {
            playerList.innerHTML = `<li>${emptyState("No players yet", "The first FPL data sync may still be running.")}</li>`;
            return;
        }
        updateStats(allPlayers);
        renderPlayers();
    } catch (error) {
        console.error(error);
        playerList.innerHTML = `<li>${errorState("Couldn’t load the players", "The free server may still be waking up. Try again shortly.", "players")}</li>`;
        playerList.querySelector("[data-retry]")?.addEventListener("click", loadPlayers);
    }
}

function renderPlayers() {
    const query = playerSearch.value.trim().toLowerCase();
    const position = playerPosition.value;
    const [sort, direction] = playerSort.value.split("-");
    const players = allPlayers
        .filter(player => !query || `${player.webName} ${player.teamName}`.toLowerCase().includes(query))
        .filter(player => !position || player.positionName === position)
        .sort((a, b) => {
            let comparison;
            if (sort === "name") comparison = a.webName.localeCompare(b.webName);
            else if (sort === "price") comparison = a.price - b.price;
            else comparison = a.totalPoints - b.totalPoints;
            return direction === "desc" ? -comparison : comparison;
        });

    document.getElementById("player-count").textContent = `${players.length} shown`;
    if (!players.length) {
        playerList.innerHTML = `<li>${emptyState("No matching players", "Try a different name or position.")}</li>`;
        return;
    }
    playerList.innerHTML = players.map(player => `
        <li class="player-card" data-pos="${escapeHtml(shortPosition(player.positionName))}">
            <div class="player-name"><span>${escapeHtml(player.webName)}</span><span class="position-chip">${escapeHtml(shortPosition(player.positionName))}</span></div>
            <div class="player-info">
                <div class="player-detail"><span>Club</span><span class="player-detail-value">${escapeHtml(player.teamName)}</span></div>
                <div class="player-detail"><span>Total points</span><span class="player-detail-value">${player.totalPoints}</span></div>
                <div class="player-detail"><span>Price</span><span class="player-detail-value price">£${player.price.toFixed(1)}m</span></div>
            </div>
        </li>`).join("");
}

function shortPosition(position) {
    return ({ Goalkeeper: "GKP", Defender: "DEF", Midfielder: "MID", Forward: "FWD" })[position] || position;
}

function updateStats(players) {
    const totalPoints = players.reduce((sum, player) => sum + player.totalPoints, 0);
    const totalPrice = players.reduce((sum, player) => sum + player.price, 0);
    document.getElementById("total-players").textContent = players.length;
    document.getElementById("avg-points").textContent = (totalPoints / players.length).toFixed(1);
    document.getElementById("avg-price").textContent = `£${(totalPrice / players.length).toFixed(1)}m`;
}

[playerSearch, playerPosition, playerSort].forEach(control => control.addEventListener("input", renderPlayers));
loadPlayers();
