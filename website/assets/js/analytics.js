/* Google Analytics 4. The measurement ID is public (it is in every page's HTML), so it lives here
   and both the landing/blog pages and the docs load this one file.

   Visitors outside Europe get analytics straight away. Visitors the browser places in Europe (EU, EEA,
   UK and Switzerland need consent) get a banner first, and nothing is loaded or stored until they
   accept. Region comes from the browser's time zone: it needs no request and no server, and where it
   cannot be told the visitor is treated as European. The banner text is the data-* attributes of this
   script's own tag, rendered from the site strings. */
(function () {
  var id = "G-NQ5LFGZT1C";
  var key = "andbible-analytics";
  var tag = document.currentScript;

  function load() {
    window.dataLayer = window.dataLayer || [];
    window.gtag = function () { window.dataLayer.push(arguments); };
    window.gtag("js", new Date());
    window.gtag("config", id);
    var script = document.createElement("script");
    script.async = true;
    script.src = "https://www.googletagmanager.com/gtag/js?id=" + id;
    document.head.appendChild(script);
  }

  function needsConsent() {
    try {
      var zone = Intl.DateTimeFormat().resolvedOptions().timeZone || "";
      return !zone || /^(Europe|Atlantic\/(Azores|Canary|Faroe|Madeira|Reykjavik))\b/.test(zone);
    } catch (e) { return true; }
  }

  function stored() {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }

  function remember(choice) {
    try { localStorage.setItem(key, choice); } catch (e) {}
  }

  function banner() {
    var data = tag.dataset;
    var root = document.createElement("div");
    root.setAttribute("role", "region");
    root.setAttribute("aria-label", data.text);
    root.style.cssText = "position:fixed;left:0;right:0;bottom:0;z-index:1000;box-sizing:border-box;" +
      "padding:12px 16px;display:flex;flex-wrap:wrap;align-items:center;gap:8px 16px;justify-content:center;" +
      "background:Canvas;color:CanvasText;border-top:1px solid #d99a2b;font:400 .9rem/1.4 system-ui,sans-serif;";
    var text = document.createElement("span");
    text.textContent = data.text + " ";
    var more = document.createElement("a");
    more.href = "/privacy/";
    more.textContent = data.privacy;
    more.style.color = "inherit";
    text.appendChild(more);
    root.appendChild(text);
    [["accept", true], ["decline", false]].forEach(function (entry) {
      var button = document.createElement("button");
      button.type = "button";
      button.textContent = data[entry[0]];
      button.style.cssText = "padding:6px 14px;border-radius:8px;border:1px solid #d99a2b;cursor:pointer;" +
        "font:inherit;" + (entry[1] ? "background:#d99a2b;color:#2a2620;" : "background:transparent;color:inherit;");
      button.addEventListener("click", function () {
        remember(entry[1] ? "granted" : "denied");
        root.remove();
        if (entry[1]) load();
      });
      root.appendChild(button);
    });
    document.body.appendChild(root);
  }

  var choice = stored();
  if (choice === "granted") load();
  else if (choice === "denied") return;
  else if (!needsConsent()) load();
  else if (document.body) banner();
  else document.addEventListener("DOMContentLoaded", banner);
})();
