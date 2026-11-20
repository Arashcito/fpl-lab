async function loadStandings() {
    const container = document.getElementById("teams-table");

    try {
        const standings = await fetchStandings();

        if (!standings || standings.length === 0) {
            container.innerHTML = '<div class="error">No standings available</div>';
            return;
        }

        const rows = standings.map(t => `
            <tr class="${zoneClass(t.position, standings.length)}">
                <td class="position">${t.position}</td>
                <td class="team-name">${escapeHtml(t.name)}</td>
                <td>${t.played}</td>
                <td>${t.won}</td>
                <td>${t.drawn}</td>
                <td>${t.lost}</td>
                <td>${t.goalsFor}</td>
                <td>${t.goalsAgainst}</td>
                <td class="${t.goalDifference > 0 ? "positive" : t.goalDifference < 0 ? "negative" : ""}">
                    ${t.goalDifference > 0 ? "+" : ""}${t.goalDifference}
                </td>
                <td class="points">${t.points}</td>
            </tr>
        `).join("");

        container.innerHTML = `
            <table class="standings-table">
                <thead>
                    <tr>
                        <th>Pos</th>
                        <th>Club</th>
                        <th>P</th>
                        <th>W</th>
                        <th>D</th>
                        <th>L</th>
                        <th>GF</th>
                        <th>GA</th>
                        <th>GD</th>
                        <th>Pts</th>
                    </tr>
                </thead>
                <tbody>${rows}</tbody>
            </table>
        `;
    } catch (error) {
        console.error("Error loading standings:", error);
        container.innerHTML = '<div class="error">Error loading standings. Please ensure the API is running.</div>';
    }
}

// Top 4: Champions League, 5th: Europa League, bottom 3: relegation
function zoneClass(position, totalTeams) {
    if (position <= 4) return "champions-league";
    if (position === 5) return "europa-league";
    if (position > totalTeams - 3) return "relegation";
    return "";
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

loadStandings();
