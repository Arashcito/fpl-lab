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
    const better = (a, b, lowerIsBetter) => (lowerIsBetter ? a < b : a > b) ? "positive" : "negative";
    return `
        <div class="stats stats-4">
            <div class="stat-card">
                <div class="stat-value">${o.gameweeksScored}</div>
                <div class="stat-label">Gameweeks scored</div>
            </div>
            <div class="stat-card">
                <div class="stat-value"><span class="${better(o.fplLabError, o.fplOfficialError, true)}">${fmt(o.fplLabError, 2)}</span>
                    <span class="vs">vs ${fmt(o.fplOfficialError, 2)}</span></div>
                <div class="stat-label">Average error: FPL Lab vs FPL official (lower is better)</div>
            </div>
            <div class="stat-card">
                <div class="stat-value"><span class="${better(o.fplLabTopPicks, o.fplOfficialTopPicks, false)}">${fmt(o.fplLabTopPicks, 1)}</span>
                    <span class="vs">vs ${fmt(o.fplOfficialTopPicks, 1)}</span></div>
                <div class="stat-label">Points per top-10 pick: FPL Lab vs FPL official</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${o.fplLabWins}–${o.fplOfficialWins}</div>
                <div class="stat-label">Gameweeks won on error: FPL Lab vs FPL official</div>
            </div>
        </div>`;
}

function gameweekTable(gws) {
    const rows = gws.map(g => {
        if (!g.fplLab) {
            return `<tr><td class="position">GW${g.event}</td><td>${escapeHtml(g.status)}</td>
                <td colspan="5" class="sub">Predictions saved, waiting for the matches</td></tr>`;
        }
        const lab = g.fplLab, off = g.fplOfficial || {};
        const win = (a, b, lower) => (a == null || b == null) ? "" : ((lower ? a < b : a > b) ? "positive" : "");
        return `
            <tr data-event="${g.event}" class="player-row">
                <td class="position">GW${g.event}</td>
                <td>${escapeHtml(g.status)}</td>
                <td><span class="${win(lab.meanAbsoluteError, off.meanAbsoluteError, true)}">${fmt(lab.meanAbsoluteError, 2)}</span>
                    <span class="sub">/ ${off.meanAbsoluteError != null ? fmt(off.meanAbsoluteError, 2) : "–"}</span></td>
                <td><span class="${win(lab.rankCorrelation, off.rankCorrelation, false)}">${fmt(lab.rankCorrelation, 2)}</span>
                    <span class="sub">/ ${off.rankCorrelation != null ? fmt(off.rankCorrelation, 2) : "–"}</span></td>
                <td><span class="${win(lab.topPicksAverage, off.topPicksAverage, false)}">${fmt(lab.topPicksAverage, 1)}</span>
                    <span class="sub">/ ${off.topPicksAverage != null ? fmt(off.topPicksAverage, 1) : "–"}</span></td>
                <td class="team-name">${escapeHtml(lab.captainName)} <span class="sub">${fmt(lab.captainPredicted)} → <strong>${lab.captainActual}</strong></span></td>
                <td class="team-name">${escapeHtml(g.bestPlayerName || "–")} <span class="sub">${g.bestPlayerPoints} pts</span></td>
            </tr>
            <tr class="breakdown-row" data-detail="${g.event}" hidden><td colspan="7"><div class="loading">Loading...</div></td></tr>`;
    }).join("");
    return `
        <h2 class="section-title">By gameweek</h2>
        <p class="hint">FPL Lab / FPL official. Green = FPL Lab did better. Click a gameweek for details.</p>
        <div class="table-scroll">
        <table class="standings-table">
            <thead><tr>
                <th>GW</th><th>Status</th><th>Average error</th><th>Ranking</th>
                <th>Top 10 picks (pts)</th><th>Our captain (pred → real)</th><th>Best player</th>
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
                <div><h4>Top predictions</h4>${playerList(d.topPredicted)}</div>
                <div><h4>Biggest over-predictions</h4>${playerList(d.overPredicted)}</div>
                <div><h4>Biggest under-predictions</h4>${playerList(d.underPredicted)}</div>
            </div>`;
        detailRow.dataset.loaded = "1";
    } catch (e) {
        detailRow.firstElementChild.innerHTML = '<div class="error">Could not load details.</div>';
    }
}

function playerList(rows) {
    return `<ul class="breakdown-list">${rows.map(r => `
        <li><span>${escapeHtml(r.name)} <span class="sub">${escapeHtml(r.team)} · ${r.position}</span></span>
            <span>${fmt(r.fplLab)} → <strong>${r.actual == null ? "–" : r.actual}</strong></span></li>`).join("")}
    </ul>`;
}
