const teamsContainer = document.getElementById("teams-table");

async function loadStandings() {
    teamsContainer.innerHTML = loadingState("Loading the league table");
    try {
        const standings = await fetchStandings();
        if (!standings?.length) {
            teamsContainer.innerHTML = emptyState("No standings available", "League data will appear after the first sync.");
            return;
        }
        const showForm = standings.some(team => team.form);
        const rows = standings.map(team => `
            <tr class="${zoneClass(team.position, standings.length)}">
                <td class="position" data-label="Position">${team.position}</td>
                <td class="team-name" data-label="Club">${team.crest ? `<img class="crest" src="${escapeHtml(team.crest)}" alt="" loading="lazy">` : ""}${escapeHtml(team.name)}</td>
                <td data-label="Played">${team.played}</td><td data-label="Won">${team.won}</td><td data-label="Drawn">${team.drawn}</td><td data-label="Lost">${team.lost}</td>
                <td data-label="Goals for">${team.goalsFor}</td><td data-label="Goals against">${team.goalsAgainst}</td>
                <td data-label="Goal difference" class="${team.goalDifference > 0 ? "positive" : team.goalDifference < 0 ? "negative" : ""}">${team.goalDifference > 0 ? "+" : ""}${team.goalDifference}</td>
                <td data-label="Points" class="points">${team.points}</td>
                ${showForm ? `<td data-label="Form" class="form">${formBadges(team.form)}</td>` : ""}
            </tr>`).join("");
        teamsContainer.innerHTML = `<div class="table-scroll"><table class="standings-table"><thead><tr><th>Pos</th><th>Club</th><th>P</th><th>W</th><th>D</th><th>L</th><th>GF</th><th>GA</th><th>GD</th><th>Pts</th>${showForm ? "<th>Form</th>" : ""}</tr></thead><tbody>${rows}</tbody></table></div>`;
    } catch (error) {
        console.error(error);
        teamsContainer.innerHTML = errorState("Couldn’t load the table", "The server may still be waking up. Try again shortly.", "teams");
        teamsContainer.querySelector("[data-retry]")?.addEventListener("click", loadStandings);
    }
}

function formBadges(form) {
    if (!form) return "";
    return form.split(",").map(result => result.trim()).filter(result => ["W", "D", "L"].includes(result)).map(result => `<span class="form-badge form-${result}">${result}</span>`).join("");
}

function zoneClass(position, totalTeams) {
    if (position <= 4) return "champions-league";
    if (position === 5) return "europa-league";
    if (position > totalTeams - 3) return "relegation";
    return "";
}

loadStandings();
