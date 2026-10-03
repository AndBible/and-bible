// Home page phone: crossfades the app screens and slowly scrolls each one up, like a user paging down.
// Without this script (or with reduced motion) the first screen stays as a static image.
// It pauses while the pointer is over the phone and while the tab is hidden.
(function () {
  "use strict";
  var SCROLL_MS = 8000; // time to scroll one screen from top to bottom
  var HOLD_MS = 1200; // rest at the top and at the bottom
  var root = document.querySelector("[data-appshots]");
  var shots = root ? Array.from(root.querySelectorAll(".phone__shot")) : [];
  var frame = root ? root.querySelector(".phone__screen") : null;
  if (shots.length < 2 || !frame || !Element.prototype.animate) return;
  if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;

  root.classList.add("is-animated");
  var index = 0;
  var current = null;
  var paused = false;
  var hovering = false;

  function show(i) {
    shots.forEach(function (shot, k) { shot.classList.toggle("is-active", k === i); });
    var image = shots[i].querySelector("img");
    var start = function () {
      var distance = Math.max(0, image.offsetHeight - frame.clientHeight);
      var previous = current;
      current = image.animate(
        [{ transform: "translateY(0)" }, { transform: "translateY(" + -distance + "px)" }],
        { duration: SCROLL_MS, delay: HOLD_MS, endDelay: HOLD_MS, easing: "linear", fill: "both" }
      );
      if (previous) setTimeout(function () { previous.cancel(); }, 800); // after the fade-out
      if (paused) current.pause();
      current.finished.then(function () {
        index = (index + 1) % shots.length;
        show(index);
      }, function () { /* cancelled */ });
    };
    if (image.complete && image.naturalHeight) start();
    else image.addEventListener("load", start, { once: true });
  }

  function refreshPause() {
    paused = hovering || document.hidden;
    if (current) paused ? current.pause() : current.play();
  }
  root.addEventListener("mouseenter", function () { hovering = true; refreshPause(); });
  root.addEventListener("mouseleave", function () { hovering = false; refreshPause(); });
  document.addEventListener("visibilitychange", refreshPause);

  show(0);
})();
