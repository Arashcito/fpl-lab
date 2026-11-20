// Shared rendering for projected points: fixture heatmap rows and point breakdowns.

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text == null ? "" : String(text);
    return div.innerHTML;
}

function fmt(n, digits = 1) {
    return Number(n).toFixed(digits);
}

// Cell colour by expected points: 0 -> red, ~3 -> neutral, 6+ -> dark green
function heatClass(points) {
    if (points <= 0.05) return "heat-0";
    if (points < 2) return "heat-1";
    if (points < 3.5) return "heat-2";
    if (points < 5) return "heat-3";
    return "heat-4";
}

function fixtureLabel(gw) {
    if (!gw.fixtures.length) return "—";
    return gw.fixtures
        .map(f => f.opponent + (f.home ? " (H)" : " (A)"))
        .join(" + ");
}

function gameweekCells(player) {
    return player.gameweeks.map(gw => `
        <td class="heat ${heatClass(gw.points)}" title="GW${gw.event}">
            <div class="fixture">${escapeHtml(fixtureLabel(gw))}</div>
            <div class="xpts">${fmt(gw.points)}</div>
        </td>`).join("");
}

function statusBadge(player) {
    if (player.chanceOfPlaying != null && player.chanceOfPlaying < 100) {
        return `<span class="badge badge-warn" title="${escapeHtml(player.news)}">${player.chanceOfPlaying}%</span>`;
    }
    if (["i", "s", "u", "n"].includes(player.status)) {
        return `<span class="badge badge-out" title="${escapeHtml(player.news)}">out</span>`;
    }
    return "";
}

const BREAKDOWN_LABELS = {
    appearance: "Playing time",
    goals: "Goals",
    assists: "Assists",
    cleanSheet: "Clean sheet",
    goalsConceded: "Goals conceded",
    saves: "Saves",
    bonus: "Bonus",
    defensiveContribution: "Defensive contribution",
    cards: "Cards",
};

function breakdownHtml(player) {
    const b = player.nextBreakdown;
    const parts = Object.entries(BREAKDOWN_LABELS)
        .filter(([key]) => Math.abs(b[key]) >= 0.01)
        .map(([key, label]) => `<li><span>${label}</span><span>${b[key] > 0 ? "+" : ""}${fmt(b[key], 2)}</span></li>`)
        .join("");
    const i = player.inputs;
    const reasons = player.reasons.map(r => `<li>${escapeHtml(r)}</li>`).join("");
    return `
        <div class="breakdown">
            <div>
                <h4>Next gameweek: where the ${fmt(player.gameweeks[0]?.points ?? 0, 2)} pts come from</h4>
                <ul class="breakdown-list">${parts}</ul>
            </div>
            <div>
                <h4>Model inputs</h4>
                <ul class="breakdown-list">
                    <li><span>Expected minutes</span><span>${fmt(i.expectedMinutes, 0)}</span></li>
                    <li><span>Chance of 60+ mins</span><span>${fmt(i.sixtyProbability * 100, 0)}%</span></li>
                    <li><span>xG per 90</span><span>${fmt(i.xg90, 2)}</span></li>
                    <li><span>xA per 90</span><span>${fmt(i.xa90, 2)}</span></li>
                    <li><span>Bonus per 90</span><span>${fmt(i.bonus90, 2)}</span></li>
                    ${player.positionId > 1 ? `<li><span>Def. contribution pts per 90</span><span>${fmt(i.defensiveContribution90, 2)}</span></li>` : ""}
                </ul>
            </div>
            ${reasons ? `<div><h4>Why</h4><ul class="reasons">${reasons}</ul></div>` : ""}
        </div>`;
}

// options: { extraColumns: [{header, cell(player)}], rowClass(player), nameBadge(player), showOwnership }
function projectionTable(players, options = {}) {
    const events = players[0].gameweeks.map(g => g.event);
    const extra = options.extraColumns || [];
    const rows = players.map(p => `
        <tr class="player-row ${options.rowClass ? options.rowClass(p) : ""}" data-id="${p.id}">
            <td class="team-name">${escapeHtml(p.name)} ${options.nameBadge ? options.nameBadge(p) : ""}${statusBadge(p)}<div class="sub">${escapeHtml(p.team)} · ${p.position}</div></td>
            ${extra.map(c => `<td>${c.cell(p)}</td>`).join("")}
            <td>£${fmt(p.price)}</td>
            ${options.showOwnership ? `<td>${fmt(p.selectedByPercent)}%</td>` : ""}
            ${gameweekCells(p)}
            <td class="points">${fmt(p.total)}</td>
        </tr>
        <tr class="breakdown-row" data-for="${p.id}" hidden>
            <td colspan="${4 + extra.length + events.length + (options.showOwnership ? 1 : 0)}">${breakdownHtml(p)}</td>
        </tr>`).join("");
    return `
        <div class="table-scroll">
        <table class="standings-table projection-table">
            <thead>
                <tr>
                    <th>Player</th>
                    ${extra.map(c => `<th>${c.header}</th>`).join("")}
                    <th>Price</th>
                    ${options.showOwnership ? "<th>Owned</th>" : ""}
                    ${events.map(e => `<th>GW${e}</th>`).join("")}
                    <th>Total</th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
        </div>`;
}

function attachBreakdownToggles(container) {
    container.querySelectorAll(".player-row").forEach(row => {
        row.addEventListener("click", () => {
            const detail = container.querySelector(`.breakdown-row[data-for="${row.dataset.id}"]`);
            if (detail) detail.hidden = !detail.hidden;
        });
    });
}
