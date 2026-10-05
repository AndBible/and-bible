/* Google Analytics 4. The measurement ID is public (it is in every page's HTML), so it lives here
   and both the landing/blog pages and the docs load this one file. */
(function () {
  var id = "G-NQ5LFGZT1C";
  window.dataLayer = window.dataLayer || [];
  function gtag() { window.dataLayer.push(arguments); }
  window.gtag = gtag;
  gtag("js", new Date());
  gtag("config", id);
  var script = document.createElement("script");
  script.async = true;
  script.src = "https://www.googletagmanager.com/gtag/js?id=" + id;
  document.head.appendChild(script);
})();
