// Left navigation height between 60em and 76.25em (see the "Docs layout" block in andbible-docs.css).
// Material's JS fits the sidebar to the visible part of .md-main only from 76.25em. Here the CSS gives the
// sidebar the full viewport height (the no-JS fallback); this shrinks it to the part of .md-main visible below
// the header and the sidebar's own top, so the nav neither runs over the footer nor slides its first items
// under the header.
(function () {
  var range = matchMedia("screen and (min-width: 60em) and (max-width: 76.234375em)");

  function fit() {
    var sidebar = document.querySelector(".md-sidebar--primary");
    var main = document.querySelector(".md-main");
    var header = document.querySelector(".md-header");
    if (!sidebar || !main) return;
    if (!range.matches) { sidebar.style.height = ""; return; }
    // At the top of the page the sidebar still sits in the flow below the header, so start from whichever is lower.
    var top = Math.max(header ? header.getBoundingClientRect().bottom : 0, sidebar.getBoundingClientRect().top);
    var visible = Math.min(window.innerHeight, main.getBoundingClientRect().bottom) - top;
    sidebar.style.height = Math.max(0, visible) + "px";
  }

  window.addEventListener("scroll", fit, { passive: true });
  window.addEventListener("resize", fit);
  range.addEventListener("change", fit);
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", fit); else fit();
})();
