// Theme: follow the OS unless the reader picked one. Runs in <head> so the
// first paint already has the right palette. Storage can be unavailable
// (private mode, blocked site data) — the OS preference then stays in charge.
(function () {
  var key = "andbible-theme";
  try {
    var saved = localStorage.getItem(key);
    if (saved === "light" || saved === "dark") document.documentElement.dataset.theme = saved;
  } catch (e) {}
  document.addEventListener("click", function (event) {
    var toggle = event.target.closest("[data-theme-toggle]");
    if (toggle) {
      var root = document.documentElement;
      var dark = root.dataset.theme
        ? root.dataset.theme === "dark"
        : matchMedia("(prefers-color-scheme: dark)").matches;
      root.dataset.theme = dark ? "light" : "dark";
      try { localStorage.setItem(key, root.dataset.theme); } catch (e) {}
    }
    var menu = event.target.closest("[data-menu-toggle]");
    if (menu) {
      var nav = document.querySelector(".topbar nav");
      var open = nav.toggleAttribute("data-open");
      menu.setAttribute("aria-expanded", String(open));
    }
  });
})();
