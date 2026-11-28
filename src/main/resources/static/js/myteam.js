const form = document.getElementById("team-form");
const entryInput = document.getElementById("entry-id");
const result = document.getElementById("result");
const submitButton = form.querySelector("button[type='submit']");

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
    submitButton.disabled = true;
    submitButton.firstElementChild.textContent = "Running the numbers…";
    result.innerHTML = loadingState("Loading your squad and testing transfer plans");
    try {
        const data = await fetchMyTeam(entryId, horizon, freeTransfers);
        result.innerHTML = render(data);
        attachBreakdownToggles(result);
        result.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (err) {
        console.error(err);
        result.innerHTML = errorState("We couldn’t analyse that team", err.message, "team");
        result.querySelector("[data-retry]")?.addEventListener("click", analyse);
    } finally {
        submitButton.disabled = false;
        submitButton.firstElementChild.textContent = "Analyse my team";
    }
}

function render(d) {
    const gwRange = d.events.length > 1 ? `GW${d.events[0]}–${d.events[d.events.length - 1]}` : `GW${d.events[0]}`;
    const plan = d.advice.recommended;
    const outIds = new Set(plan ? plan.transfers.map(t => t.out.id) : []);
    return `
        <section class="scoreboard" aria-label="Squad summary">
            <div class="team-banner">
                <span class="eyebrow">Your squad</span>
                <div class="team-title">${escapeHtml(d.teamName)}</div>
                <div class="sub">${escapeHtml(d.managerName)}${d.overallRank ? ` · Rank ${d.overallRank.toLocaleString()}` : ""} · ${d.overallPoints} pts</div>
            </div>
            <div class="stats">
                <div class="stat-card">
                    <span class="stat-kicker">Bank</span>
                    <div class="stat-value">£${fmt(d.bank)}m</div>
                    <div class="stat-label">To spend</div>
                </div>
                <div class="stat-card">
                    <span class="stat-kicker">Free transfers</span>
                    <div class="stat-value">${d.freeTransfers}</div>
                    <div class="stat-label">${d.freeTransfersEstimated ? "Estimated" : "From FPL"}</div>
                </div>
                <div class="stat-card">
                    <span class="stat-kicker">Projected</span>
                    <div class="stat-value">${fmt(d.squadValue)}</div>
                    <div class="stat-label">Points ${gwRange}, best XI + captain</div>
                </div>
            </div>
        </section>

        <div class="section-heading"><div><span class="eyebrow">Before the GW${d.nextEvent} deadline</span><h2>Transfers and captain</h2></div></div>
        <div class="decision-board">
            ${renderAdvice(d)}
            ${renderCaptains(d)}
        </div>

        <div class="section-heading"><div><span class="eyebrow">Team sheet</span><h2>Your XI for GW${d.nextEvent}</h2></div>
        <p class="hint">The number on each shirt is that player's expected points for GW${d.nextEvent}. A red OUT tag marks a suggested transfer.</p></div>
        ${renderPitch(d, outIds)}

        <div class="section-heading"><div><span class="eyebrow">Fixtures</span><h2>Expected points, ${gwRange}</h2></div>
        <p class="hint">Greener cells mean more expected points. Select a player for the breakdown.</p></div>
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
                return s.captain ? '<span class="badge badge-captain" title="Captain">C</span> '
                    : s.viceCaptain ? '<span class="badge badge-vice" title="Vice-captain">V</span> ' : "";
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

// The starting XI in formation, goalkeeper at the top as on the FPL pick screen, then the bench.
function renderPitch(d, outIds) {
    const lines = ["GKP", "DEF", "MID", "FWD"].map(position => d.squad.filter(s => s.starter && s.player.position === position));
    const bench = d.squad.filter(s => !s.starter);
    return `
        <div class="team-sheet pitch-surface">
            <div class="pitch">
                ${lines.filter(line => line.length).map(line => `<div class="pitch-line">${line.map(s => playerToken(s, outIds)).join("")}</div>`).join("")}
            </div>
            <div class="pitch-bench"><span class="bench-label">Bench</span>${bench.map(s => playerToken(s, outIds)).join("")}</div>
        </div>`;
}

function playerToken(s, outIds) {
    const p = s.player;
    const next = p.gameweeks[0];
    const injured = ["i", "s", "u", "n"].includes(p.status);
    const doubtful = !injured && p.chanceOfPlaying != null && p.chanceOfPlaying < 100;
    const classes = ["player-token", p.position === "GKP" ? "keeper" : "", injured ? "flag-out" : ""].join(" ");
    const label = `${p.name}, ${p.team}, ${fmt(next ? next.points : 0)} expected points`
        + (s.captain ? ", captain" : s.viceCaptain ? ", vice-captain" : "")
        + (outIds.has(p.id) ? ", suggested transfer out" : "")
        + (injured || doubtful ? `, ${p.news || "fitness doubt"}` : "");
    return `
        <div class="${classes}" role="img" aria-label="${escapeHtml(label)}" title="${escapeHtml(p.news || "")}">
            <span class="shirt">${fmt(next ? next.points : 0)}</span>
            ${s.captain ? '<span class="armband">C</span>' : s.viceCaptain ? '<span class="armband vice">V</span>' : ""}
            ${outIds.has(p.id) ? '<span class="sub-tag">OUT</span>' : injured ? '<span class="card red"></span>' : doubtful ? '<span class="card"></span>' : ""}
            <span class="token-name">${escapeHtml(p.name)}</span>
            <span class="token-fix">${escapeHtml(next ? fixtureLabel(next) : "")}</span>
        </div>`;
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
            <td class="team-name" data-label="Move">${escapeHtml(t.out.name)} → ${escapeHtml(t.in.name)}</td>
            <td data-label="Player">${escapeHtml(t.in.team)} · ${t.in.position}</td>
            <td data-label="Price">£${fmt(t.buyPrice)}</td>
            <td data-label="Gain" class="${p.netGain >= 0 ? "positive" : "negative"}">${p.netGain >= 0 ? "+" : ""}${fmt(p.netGain)}</td>
        </tr>`;
    }).join("");

    return `
        <div class="advice ${a.rollTransfer ? "advice-roll" : ""}">
            <div class="card-head"><h3>Transfers</h3><span class="status-chip ${a.rollTransfer ? "hold" : ""}">${a.rollTransfer ? "Roll the transfer" : "Make a move"}</span></div>
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
                <div class="label">▼ OUT</div>
                <div class="name">${escapeHtml(t.out.name)}</div>
                <div class="sub">${escapeHtml(t.out.team)} · ${t.out.position} · sells for £${fmt(t.sellingPrice)}</div>
                <div class="xp">${fmt(t.out.total)} <span class="sub">xPts</span></div>
            </div>
            <div class="arrow" aria-hidden="true">→</div>
            <div class="transfer-side in">
                <div class="label">▲ IN</div>
                <div class="name">${escapeHtml(t.in.name)}</div>
                <div class="sub">${escapeHtml(t.in.team)} · ${t.in.position} · costs £${fmt(t.buyPrice)}</div>
                <div class="xp">${fmt(t.in.total)} <span class="sub">xPts</span></div>
                ${reasons ? `<ul class="reasons">${reasons}</ul>` : ""}
            </div>
        </div>`;
}

function renderCaptains(d) {
    const items = d.captainOptions.map((c, i) => `
        <li class="captain-option">
            <span class="rank" aria-label="${i === 0 ? "Captain" : i === 1 ? "Vice-captain" : `Option ${i + 1}`}">${i === 0 ? "C" : i === 1 ? "V" : i + 1}</span>
            <span class="name">${escapeHtml(c.player.name)} <span class="sub">${escapeHtml(c.player.team)} · ${escapeHtml(fixtureLabel(c.player.gameweeks[0]))}</span></span>
            <span class="xp">${fmt(c.points)} <small>xPts</small></span>
            <span class="why">${escapeHtml(c.player.reasons.slice(0, 2).join(" · "))}</span>
        </li>`).join("");
    return `
        <section class="captain-lane">
            <div class="card-head"><h3>Armband</h3><span class="sub">GW${d.nextEvent}</span></div>
            <ol class="captain-list">${items}</ol>
        </section>`;
}
