// Docs theme = the site theme. The landing/blog pages keep the reader's choice in localStorage
// ("andbible-theme": light|dark, see assets/js/theme.js); here it is mapped onto Zensical's colour
// schemes (default/slate). Zensical's own palette form and its __palette storage are not rendered, so
// nothing fights back. Loaded from <head>: the scheme is on <body> as soon as the parser creates it,
// before the first paint. Storage can be unavailable; the OS preference then decides.
(function () {
  var key = "andbible-theme";
  var root = document.documentElement;
  var dark = matchMedia("(prefers-color-scheme: dark)");

  function saved() {
    try {
      var value = localStorage.getItem(key);
      return value === "light" || value === "dark" ? value : null;
    } catch (e) { return null; }
  }
  function effective() { return saved() || (dark.matches ? "dark" : "light"); }
  function apply() {
    var theme = effective();
    if (saved()) root.dataset.theme = theme; else delete root.dataset.theme;
    if (document.body) document.body.setAttribute("data-md-color-scheme", theme === "dark" ? "slate" : "default");
  }

  apply();
  if (!document.body) {
    var watch = new MutationObserver(function () {
      if (document.body) { watch.disconnect(); apply(); }
    });
    watch.observe(root, { childList: true });
  }
  dark.addEventListener("change", apply);
  window.addEventListener("storage", function (event) { if (event.key === key) apply(); });
  document.addEventListener("click", function (event) {
    if (!event.target.closest("[data-theme-toggle]")) return;
    var next = effective() === "dark" ? "light" : "dark";
    try { localStorage.setItem(key, next); } catch (e) {}
    root.dataset.theme = next;
    document.body.setAttribute("data-md-color-scheme", next === "dark" ? "slate" : "default");
  });
})();
