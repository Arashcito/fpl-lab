const form = document.getElementById("team-form");
const entryInput = document.getElementById("entry-id");
const result = document.getElementById("result");

// remember the last team ID on this device
try {
    const saved = localStorage.getItem("fpl-lab-entry-id");
    if (saved) entryInput.value = saved;
} catch (e) { /* storage unavailable */ }

form.addEventListener("submit", e => {
    e.preventDefault();
    analyse();
});

if (entryInput.value) analyse();

async function analyse() {
    const entryId = entryInput.value.trim();
    if (!entryId) return;
    try { localStorage.setItem("fpl-lab-entry-id", entryId); } catch (e) { /* ignore */ }

    const horizon = document.getElementById("horizon").value;
    const freeTransfers = document.getElementById("free-transfers").value;
    result.innerHTML = '<div class="loading">Loading your team and running the numbers...</div>';
    try {
        const data = await fetchMyTeam(entryId, horizon, freeTransfers);
        result.innerHTML = render(data);
        attachBreakdownToggles(result);
    } catch (err) {
        console.error(err);
        result.innerHTML = `<div class="error">${escapeHtml(err.message)}</div>`;
    }
}

function render(d) {
    const gwRange = d.events.length > 1 ? `GW${d.events[0]}–${d.events[d.events.length - 1]}` : `GW${d.events[0]}`;
    return `
        <div class="team-banner">
            <div>
                <div class="team-title">${escapeHtml(d.teamName)}</div>
                <div class="sub">${escapeHtml(d.managerName)}${d.overallRank ? ` · Rank ${d.overallRank.toLocaleString()}` : ""} · ${d.overallPoints} pts</div>
            </div>
        </div>

        <div class="stats">
            <div class="stat-card">
                <div class="stat-value">£${fmt(d.bank)}m</div>
                <div class="stat-label">In the bank</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${d.freeTransfers}</div>
                <div class="stat-label">Free transfers${d.freeTransfersEstimated ? " (estimated)" : ""}</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${fmt(d.squadValue)}</div>
                <div class="stat-label">Projected pts ${gwRange} (best XI + captain)</div>
            </div>
        </div>

        ${renderAdvice(d)}
        ${renderCaptains(d)}

        <h2 class="section-title">Your squad, ${gwRange}</h2>
        <p class="hint">Expected points per gameweek (H = home, A = away). Click a player for the breakdown.</p>
        ${projectionTable(d.squad.map(s => s.player), {
            extraColumns: [{
                header: "Sell",
                cell: p => {
                    const s = d.squad.find(x => x.player.id === p.id);
                    return `£${fmt(s.sellingPrice)}`;
                }
            }],
            nameBadge: p => {
                const s = d.squad.find(x => x.player.id === p.id);
                return s.captain ? '<span class="badge badge-captain">C</span> '
                    : s.viceCaptain ? '<span class="badge badge-vice">V</span> ' : "";
            },
            rowClass: p => {
                const s = d.squad.find(x => x.player.id === p.id);
                const firstBench = d.squad.find(x => !x.starter);
                return (s.starter ? "starter" : "bench")
                    + (s.captain ? " captain" : "")
                    + (firstBench && firstBench.player.id === p.id ? " first-bench" : "");
            }
        })}
    `;
}

function renderAdvice(d) {
    const a = d.advice;
    const plan = a.recommended;
    let body = "";
    if (plan && plan.transfers.length) {
        body = plan.transfers.map(t => transferCard(t)).join("");
    }
    const alternatives = (a.alternatives || []).filter(p => p.transfers.length);
    const altRows = alternatives.map(p => {
        const t = p.transfers[0];
        return `<tr>
            <td class="team-name">${escapeHtml(t.out.name)} → ${escapeHtml(t.in.name)}</td>
            <td>${escapeHtml(t.in.team)} · ${t.in.position}</td>
            <td>£${fmt(t.buyPrice)}</td>
            <td class="${p.netGain >= 0 ? "positive" : "negative"}">${p.netGain >= 0 ? "+" : ""}${fmt(p.netGain)}</td>
        </tr>`;
    }).join("");

    return `
        <div class="advice ${a.rollTransfer ? "advice-roll" : ""}">
            <h2 class="section-title">Transfer advice</h2>
            <p class="advice-summary">${escapeHtml(a.summary)}</p>
            ${body}
            ${altRows ? `
                <details class="alternatives">
                    <summary>Other single transfers worth considering</summary>
                    <table class="standings-table">
                        <thead><tr><th>Move</th><th>Player</th><th>Price</th><th>Gain (pts)</th></tr></thead>
                        <tbody>${altRows}</tbody>
                    </table>
                </details>` : ""}
        </div>`;
}

function transferCard(t) {
    const reasons = t.in.reasons.slice(0, 4).map(r => `<li>${escapeHtml(r)}</li>`).join("");
    return `
        <div class="transfer">
            <div class="transfer-side out">
                <div class="label">OUT</div>
                <div class="name">${escapeHtml(t.out.name)}</div>
                <div class="sub">${escapeHtml(t.out.team)} · ${t.out.position} · sells for £${fmt(t.sellingPrice)}</div>
                <div class="xp">${fmt(t.out.total)} xPts</div>
            </div>
            <div class="arrow">→</div>
            <div class="transfer-side in">
                <div class="label">IN</div>
                <div class="name">${escapeHtml(t.in.name)}</div>
                <div class="sub">${escapeHtml(t.in.team)} · ${t.in.position} · costs £${fmt(t.buyPrice)}</div>
                <div class="xp">${fmt(t.in.total)} xPts</div>
                ${reasons ? `<ul class="reasons">${reasons}</ul>` : ""}
            </div>
        </div>`;
}

function renderCaptains(d) {
    const items = d.captainOptions.map((c, i) => `
        <li class="captain-option">
            <span class="rank">${i === 0 ? "C" : i === 1 ? "V" : i + 1}</span>
            <span class="name">${escapeHtml(c.player.name)} <span class="sub">${escapeHtml(c.player.team)} · ${escapeHtml(fixtureLabel(c.player.gameweeks[0]))}</span></span>
            <span class="xp">${fmt(c.points)} xPts</span>
            <span class="why">${escapeHtml(c.player.reasons.slice(0, 2).join(" · "))}</span>
        </li>`).join("");
    return `
        <h2 class="section-title">Captain picks for GW${d.nextEvent}</h2>
        <ol class="captain-list">${items}</ol>`;
}
