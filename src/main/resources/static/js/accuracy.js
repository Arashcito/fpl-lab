const container = document.getElementById("accuracy");

loadAccuracy();

async function loadAccuracy() {
    try {
        const report = await fetchJson("/api/accuracy");
        container.innerHTML = render(report);
        container.querySelectorAll("tr[data-event]").forEach(row =>
            row.addEventListener("click", () => toggleDetail(row)));
    } catch (e) {
        console.error(e);
        container.innerHTML = '<div class="error">Could not load accuracy data. Is the API running?</div>';
    }
}

function render(report) {
    const gws = report.gameweeks;
    if (!gws.length) {
        return `<div class="advice advice-roll">
            <p class="advice-summary">No predictions saved yet.</p>
            <p>They are saved automatically before the next deadline while the app is running.</p>
        </div>`;
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
                <div class="stat-value">${o.gameweeksScored}</div>
                <div class="stat-label">Gameweeks scored${o.mlGameweeksScored && o.mlGameweeksScored < o.gameweeksScored
                    ? ` (ML model: ${o.mlGameweeksScored})` : ""}</div>
            </div>
            <div class="stat-card">
                <div class="stat-models">${compare(errors, 2, true)}</div>
                <div class="stat-label">Average error (lower is better)</div>
            </div>
            <div class="stat-card">
                <div class="stat-models">${compare(top, 1, false)}</div>
                <div class="stat-label">Points per top-10 pick</div>
            </div>
            <div class="stat-card">
                <div class="stat-models">${compare(wins, 0, false)}</div>
                <div class="stat-label">Gameweeks with the lowest error</div>
            </div>
        </div>`;
}

function gameweekTable(gws) {
    const rows = gws.map(g => {
        if (!g.fplLab) {
            return `<tr><td class="position">GW${g.event}</td><td>${escapeHtml(g.status)}</td>
                <td colspan="5" class="sub">Predictions saved, waiting for the matches ·
                    <a href="/api/accuracy/${g.event}/download">download JSON</a></td></tr>`;
        }
        const metric = (field, digits, lower) =>
            compare(MODELS.map(m => ({ label: m.label, value: g[m.key] ? g[m.key][field] : null })), digits, lower);
        const lab = g.fplLab;
        return `
            <tr data-event="${g.event}" class="player-row">
                <td class="position">GW${g.event}</td>
                <td>${escapeHtml(g.status)}</td>
                <td>${metric("meanAbsoluteError", 2, true)}</td>
                <td>${metric("rankCorrelation", 2, false)}</td>
                <td>${metric("topPicksAverage", 1, false)}</td>
                <td class="team-name">${escapeHtml(lab.captainName)} <span class="sub">${fmt(lab.captainPredicted)} → <strong>${lab.captainActual}</strong></span></td>
                <td class="team-name">${escapeHtml(g.bestPlayerName || "–")} <span class="sub">${g.bestPlayerPoints} pts</span></td>
            </tr>
            <tr class="breakdown-row" data-detail="${g.event}" hidden><td colspan="7"><div class="loading">Loading...</div></td></tr>`;
    }).join("");
    return `
        <h2 class="section-title">By gameweek</h2>
        <p class="hint">✓ = best of the models that gameweek. Click a gameweek for details.</p>
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
        detailRow.firstElementChild.innerHTML = '<div class="error">Could not load details.</div>';
    }
}

function playerList(rows) {
    return `<ul class="breakdown-list">${rows.map(r => `
        <li><span>${escapeHtml(r.name)} <span class="sub">${escapeHtml(r.team)} · ${r.position}</span></span>
            <span>${fmt(r.fplLab)}${r.fplLabMl != null ? ` <span class="sub">ML ${fmt(r.fplLabMl)}</span>` : ""} → <strong>${r.actual == null ? "–" : r.actual}</strong></span></li>`).join("")}
    </ul>`;
}
