function escapeHtml(value) {
    const div = document.createElement("div");
    div.textContent = value == null ? "" : String(value);
    return div.innerHTML;
}

function fmt(value, digits = 1) {
    return Number(value || 0).toFixed(digits);
}

function loadingState(label = "Loading data") {
    return `<div class="state-card loading-state" role="status" aria-live="polite">
        <div class="loader" aria-hidden="true"><span></span><span></span><span></span></div>
        <div>
            <strong>${escapeHtml(label)}</strong>
            <p class="wake-message">The free server may need up to a minute to wake after being idle. Keep this tab open—we’re on it.</p>
        </div>
    </div>`;
}

function errorState(title, message, retryAction = "") {
    return `<div class="state-card error-state" role="alert">
        <span class="state-icon" aria-hidden="true">!</span>
        <div><strong>${escapeHtml(title)}</strong><p>${escapeHtml(message)}</p></div>
        ${retryAction ? `<button class="button button-secondary button-small" type="button" data-retry="${escapeHtml(retryAction)}">Try again</button>` : ""}
    </div>`;
}

function emptyState(title, message) {
    return `<div class="state-card empty-state">
        <span class="state-icon" aria-hidden="true">↗</span>
        <div><strong>${escapeHtml(title)}</strong><p>${escapeHtml(message)}</p></div>
    </div>`;
}
