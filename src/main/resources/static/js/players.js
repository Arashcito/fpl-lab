async function loadPlayers() {
    const list = document.getElementById("players");
    
    try {
        const players = await fetchPlayers();
        
        if (!players || players.length === 0) {
            list.innerHTML = '<li class="error">No players found</li>';
            return;
        }

        // Update stats
        updateStats(players);

        // Clear loading message
        list.innerHTML = '';

        // Display players
        players.forEach(p => {
            const li = document.createElement("li");
            li.className = "player-card";
            
            li.innerHTML = `
                <div class="player-name">${escapeHtml(p.webName)}</div>
                <div class="player-info">
                    <div class="player-detail">
                        <span class="player-detail-label">Team:</span>
                        <span class="player-detail-value team">${escapeHtml(p.teamName)}</span>
                    </div>
                    <div class="player-detail">
                        <span class="player-detail-label">Position:</span>
                        <span class="player-detail-value">${escapeHtml(p.positionName)}</span>
                    </div>
                    <div class="player-detail">
                        <span class="player-detail-label">Points:</span>
                        <span class="player-detail-value points">${p.totalPoints} pts</span>
                    </div>
                    <div class="player-detail">
                        <span class="player-detail-label">Price:</span>
                        <span class="player-detail-value price">£${p.price.toFixed(1)}m</span>
                    </div>
                </div>
            `;
            
            list.appendChild(li);
        });
    } catch (error) {
        console.error("Error loading players:", error);
        list.innerHTML = '<li class="error">Error loading players. Please check the console for details or ensure the API is running.</li>';
    }
}

function updateStats(players) {
    const totalPlayers = players.length;
    const totalPoints = players.reduce((sum, p) => sum + p.totalPoints, 0);
    const avgPoints = totalPlayers > 0 ? (totalPoints / totalPlayers).toFixed(1) : 0;
    const totalPrice = players.reduce((sum, p) => sum + p.price, 0);
    const avgPrice = totalPlayers > 0 ? (totalPrice / totalPlayers).toFixed(1) : 0;

    document.getElementById("total-players").textContent = totalPlayers;
    document.getElementById("avg-points").textContent = avgPoints;
    document.getElementById("avg-price").textContent = `£${avgPrice}m`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

loadPlayers();
