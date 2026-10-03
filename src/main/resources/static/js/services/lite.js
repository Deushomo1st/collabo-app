// Weaker phones (4 cores or fewer, or 4 GB of memory or less, or "save data" on) get the lite look: the frosted-glass blur is switched off everywhere
// (global.css, html.lite) and panels are a little more solid instead. Blur re-draws behind a panel every time the page moves, which is what slows those phones down.
const weak = (navigator.deviceMemory && navigator.deviceMemory <= 4) || navigator.hardwareConcurrency <= 4 || matchMedia('(prefers-reduced-data: reduce)').matches;
if (weak) document.documentElement.classList.add('lite');
