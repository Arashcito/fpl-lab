(function () {
    let saved = null;
    try { saved = localStorage.getItem("fpl-lab-theme"); } catch (error) { /* storage may be unavailable */ }
    const preferred = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    document.documentElement.dataset.theme = saved || preferred;
})();
