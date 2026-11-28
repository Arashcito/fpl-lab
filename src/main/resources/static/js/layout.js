(function () {
    const pages = [
        { id: "myteam", href: "myteam.html", label: "My Team" },
        { id: "picks", href: "picks.html", label: "Top Picks" },
        { id: "accuracy", href: "accuracy.html", label: "Accuracy" },
        { id: "teams", href: "teams.html", label: "League Table" },
        { id: "players", href: "players.html", label: "Players" },
    ];

    const header = document.querySelector("[data-site-header]");
    if (header) {
        const current = header.dataset.page;
        header.innerHTML = `
            <header class="site-header">
                <a class="brand" href="myteam.html" aria-label="FPL Lab home">
                    <span class="brand-mark" aria-hidden="true"></span>
                    <span><strong>FPL</strong> Lab</span>
                </a>
                <button class="nav-toggle" type="button" aria-expanded="false" aria-controls="primary-nav">
                    <span class="sr-only">Toggle navigation</span><span></span><span></span><span></span>
                </button>
                <nav class="nav" id="primary-nav" aria-label="Primary navigation">
                    ${pages.map(page => `<a href="${page.href}"${current === page.id ? ' class="active" aria-current="page"' : ""}>${page.label}</a>`).join("")}
                </nav>
                <button class="theme-toggle" type="button" aria-label="Switch colour theme" title="Switch colour theme">
                    <span class="theme-sun" aria-hidden="true">☀</span><span class="theme-moon" aria-hidden="true">☾</span>
                </button>
            </header>`;

        const navToggle = header.querySelector(".nav-toggle");
        const nav = header.querySelector(".nav");
        navToggle.addEventListener("click", () => {
            const open = navToggle.getAttribute("aria-expanded") === "true";
            navToggle.setAttribute("aria-expanded", String(!open));
            nav.classList.toggle("open", !open);
        });

        header.querySelector(".theme-toggle").addEventListener("click", () => {
            const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
            document.documentElement.dataset.theme = next;
            try { localStorage.setItem("fpl-lab-theme", next); } catch (error) { /* storage may be unavailable */ }
        });
    }

    const footer = document.querySelector("[data-site-footer]");
    if (footer) {
        footer.innerHTML = `<footer class="site-footer">
            <p><strong>FPL Lab</strong> · Make the call before the deadline, not after the final whistle.</p>
            <p class="sub"><a href="find-id.html">Find your team ID</a> · Unofficial and not affiliated with the Premier League.</p>
        </footer>`;
    }
})();
