const container = document.getElementById("accuracy");

loadAccuracy();

async function loadAccuracy() {
    container.innerHTML = loadingState("Loading prediction results");
    try {
        const report = await fetchJson("/api/accuracy");
        container.innerHTML = render(report);
        container.querySelectorAll("tr[data-event]").forEach(row => {
            row.addEventListener("click", () => toggleDetail(row));
            row.addEventListener("keydown", event => {
                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    toggleDetail(row);
                }
            });
        });
    } catch (e) {
        console.error(e);
        container.innerHTML = errorState("Couldn’t load the accuracy report", "The server may still be waking up. Wait a moment and try again.", "accuracy");
        container.querySelector("[data-retry]")?.addEventListener("click", loadAccuracy);
    }
}

function render(report) {
    const gws = report.gameweeks;
    if (!gws.length) {
        return emptyState("No predictions saved yet", "Predictions are captured automatically before the next gameweek deadline.");
    }
    return overallCards(report.overall, gws) + gameweekTable(gws);
}

// The three models compared on this page
const MODELS = [
    { key: "fplLab", label: "Formula" },
    { key: "fplLabMl", label: "ML model" },
    { key: "fplOfficial", label: "FPL official" },
];

// List of models with their value; the best one is highlighted
function compare(values, digits, lowerIsBetter) {
    const present = values.filter(v => v.value != null);
    if (!present.length) return "–";
    const best = present.reduce((a, b) => (lowerIsBetter ? b.value < a.value : b.value > a.value) ? b : a);
    return present.map(v => `<div class="model-value ${v === best && present.length > 1 ? "positive" : ""}">
        <span class="sub">${v.label}</span> ${fmt(v.value, digits)}</div>`).join("");
}

function overallCards(o, gws) {
    if (!o.gameweeksScored) {
        const upcoming = gws.find(g => g.status === "upcoming") || gws[0];
        const when = upcoming.savedAt ? new Date(upcoming.savedAt).toLocaleString() : "";
        return `<div class="advice">
            <p class="advice-summary">Tracking has started.</p>
            <p>Predictions for <strong>GW${upcoming.event}</strong> are saved (last update ${escapeHtml(when)}).
               They keep updating until the deadline, then freeze. The first results appear here once GW${upcoming.event} is played.</p>
        </div>`;
    }
    const errors = [
        { label: "Formula", value: o.fplLabError },
        { label: "ML model", value: o.fplLabMlError },
        { label: "FPL official", value: o.fplOfficialError },
    ];
    const top = [
        { label: "Formula", value: o.fplLabTopPicks },
        { label: "ML model", value: o.fplLabMlTopPicks },
        { label: "FPL official", value: o.fplOfficialTopPicks },
    ];
    const wins = [
        { label: "Formula", value: o.fplLabWins },
        ...(o.mlGameweeksScored ? [{ label: "ML model", value: o.fplLabMlWins }] : []),
        { label: "FPL official", value: o.fplOfficialWins },
    ];
    return `
        <div class="stats stats-4">
            <div class="stat-card">
                <span class="stat-kicker">Sample</span>
                <div class="stat-value">${o.gameweeksScored}</div>
                <div class="stat-label">Gameweeks scored${o.mlGameweeksScored && o.mlGameweeksScored < o.gameweeksScored
                    ? ` (ML model: ${o.mlGameweeksScored})` : ""}</div>
            </div>
            <div class="stat-card">
                <span class="stat-kicker">Precision</span>
                <div class="stat-models">${compare(errors, 2, true)}</div>
                <div class="stat-label">Average error (lower is better)</div>
            </div>
            <div class="stat-card">
                <span class="stat-kicker">Selection</span>
                <div class="stat-models">${compare(top, 1, false)}</div>
                <div class="stat-label">Points per top-10 pick</div>
            </div>
            <div class="stat-card">
                <span class="stat-kicker">Scoreboard</span>
                <div class="stat-models">${compare(wins, 0, false)}</div>
                <div class="stat-label">Gameweeks with the lowest error</div>
            </div>
        </div>`;
}

function gameweekTable(gws) {
    const rows = gws.map(g => {
        if (!g.fplLab) {
            return `<tr><td class="position" data-label="Gameweek">GW${g.event}</td><td data-label="Status">${escapeHtml(g.status)}</td>
                <td colspan="5" data-label="Update" class="sub">Predictions saved, waiting for the matches ·
                    <a href="/api/accuracy/${g.event}/download">download JSON</a></td></tr>`;
        }
        const metric = (field, digits, lower) =>
            compare(MODELS.map(m => ({ label: m.label, value: g[m.key] ? g[m.key][field] : null })), digits, lower);
        const lab = g.fplLab;
        return `
            <tr data-event="${g.event}" class="player-row" tabindex="0" role="button" aria-expanded="false" aria-label="Show GW${g.event} accuracy details">
                <td class="position" data-label="Gameweek">GW${g.event}</td>
                <td data-label="Status">${escapeHtml(g.status)}</td>
                <td data-label="Average error">${metric("meanAbsoluteError", 2, true)}</td>
                <td data-label="Ranking">${metric("rankCorrelation", 2, false)}</td>
                <td data-label="Top 10 picks">${metric("topPicksAverage", 1, false)}</td>
                <td class="team-name" data-label="Formula captain">${escapeHtml(lab.captainName)} <span class="sub">${fmt(lab.captainPredicted)} → <strong>${lab.captainActual}</strong></span></td>
                <td class="team-name" data-label="Best player">${escapeHtml(g.bestPlayerName || "–")} <span class="sub">${g.bestPlayerPoints} pts</span></td>
            </tr>
            <tr class="breakdown-row" data-detail="${g.event}" hidden><td colspan="7">${loadingState("Loading gameweek details")}</td></tr>`;
    }).join("");
    return `
        <div class="section-heading"><div><span class="eyebrow">Gameweek archive</span><h2>By gameweek</h2></div>
        <p class="hint">Green = best model. Select a gameweek for details.</p></div>
        <div class="table-scroll">
        <table class="standings-table">
            <thead><tr>
                <th>GW</th><th>Status</th><th>Average error</th><th>Ranking</th>
                <th>Top 10 picks (pts)</th><th>Formula's captain (pred → real)</th><th>Best player</th>
            </tr></thead>
            <tbody>${rows}</tbody>
        </table>
        </div>`;
}

async function toggleDetail(row) {
    const detailRow = container.querySelector(`tr[data-detail="${row.dataset.event}"]`);
    detailRow.hidden = !detailRow.hidden;
    row.setAttribute("aria-expanded", String(!detailRow.hidden));
    if (detailRow.hidden || detailRow.dataset.loaded) return;
    try {
        const d = await fetchJson(`/api/accuracy/${row.dataset.event}`);
        detailRow.firstElementChild.innerHTML = `
            <div class="breakdown">
                <div><h4>Formula's top predictions</h4>${playerList(d.topPredicted)}</div>
                <div><h4>Biggest over-predictions</h4>${playerList(d.overPredicted)}</div>
                <div><h4>Biggest under-predictions</h4>${playerList(d.underPredicted)}</div>
            </div>
            <p class="hint"><a href="/api/accuracy/${row.dataset.event}/download">Download all GW${row.dataset.event} predictions (JSON)</a></p>`;
        detailRow.dataset.loaded = "1";
    } catch (e) {
        detailRow.firstElementChild.innerHTML = errorState("Couldn’t load gameweek details", "Please close this row and try again.");
    }
}

function playerList(rows) {
    return `<ul class="breakdown-list">${rows.map(r => `
        <li><span>${escapeHtml(r.name)} <span class="sub">${escapeHtml(r.team)} · ${r.position}</span></span>
            <span>${fmt(r.fplLab)}${r.fplLabMl != null ? ` <span class="sub">ML ${fmt(r.fplLabMl)}</span>` : ""} → <strong>${r.actual == null ? "–" : r.actual}</strong></span></li>`).join("")}
    </ul>`;
}
