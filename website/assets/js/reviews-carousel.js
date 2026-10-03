// Home page reviews: turns the plain list of review cards into a one-at-a-time carousel.
// Without this script (or when it fails) all cards stay visible as a list.
// Auto-advance runs every 10 s unless the visitor prefers reduced motion; it pauses on mouse hover,
// keyboard focus and a hidden tab, and stops for good once the visitor uses prev/next or the arrow keys.
(function () {
  "use strict";
  var INTERVAL_MS = 10000;
  var root = document.querySelector("[data-reviews-carousel]");
  var slides = root ? Array.from(root.querySelectorAll(".review")) : [];
  if (slides.length < 2) return;

  var data = root.dataset;
  var fill = function (pattern, n) {
    return pattern.replace("{n}", n).replace("{total}", slides.length);
  };
  var reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
  var index = 0;
  var playing = !reduced; // the visitor's intent: auto-advance wanted
  var hovering = false;
  var focused = false;
  var timer = 0;

  var button = function (glyph, label, className) {
    var el = document.createElement("button");
    el.type = "button";
    el.className = "icon-button " + (className || "");
    el.textContent = glyph;
    el.setAttribute("aria-label", label);
    return el;
  };
  var prev = button("‹", data.prev);
  var next = button("›", data.next);
  var toggle = button("", "", "review-toggle");
  var position = document.createElement("span");
  position.className = "review-position";
  position.setAttribute("aria-live", "off");
  var announcer = document.createElement("div");
  announcer.className = "sr-only";
  announcer.setAttribute("aria-live", "polite");
  var controls = document.createElement("div");
  controls.className = "review-controls";
  controls.append(prev, position, next);
  if (!reduced) controls.append(toggle);
  root.append(controls, announcer);

  function show(i, announce) {
    index = (i + slides.length) % slides.length;
    slides.forEach(function (slide, n) {
      var active = n === index;
      slide.classList.toggle("is-active", active);
      slide.setAttribute("aria-hidden", String(!active));
      slide.inert = !active;
    });
    position.textContent = fill(data.position, index + 1);
    if (announce) announcer.textContent = position.textContent;
  }

  function schedule() {
    clearTimeout(timer);
    if (playing && !hovering && !focused && !document.hidden) {
      timer = setTimeout(function () { show(index + 1, false); schedule(); }, INTERVAL_MS);
    }
  }

  function renderToggle() {
    var label = playing ? data.pause : data.play;
    toggle.textContent = playing ? "❚❚" : "▶";
    toggle.setAttribute("aria-label", label);
    toggle.title = label;
  }

  function userMoved(to) {
    playing = false;
    renderToggle();
    show(to, true);
    schedule();
  }

  prev.addEventListener("click", function () { userMoved(index - 1); });
  next.addEventListener("click", function () { userMoved(index + 1); });
  toggle.addEventListener("click", function () {
    playing = !playing;
    focused = false; // pressing play must start the rotation even though the button has keyboard focus
    renderToggle();
    schedule();
  });
  root.addEventListener("keydown", function (event) {
    if (event.key === "ArrowLeft") userMoved(index - 1);
    else if (event.key === "ArrowRight") userMoved(index + 1);
    else return;
    event.preventDefault();
  });
  root.addEventListener("pointerenter", function (event) {
    if (event.pointerType === "mouse") { hovering = true; schedule(); }
  });
  root.addEventListener("pointerleave", function () { hovering = false; schedule(); });
  root.addEventListener("focusin", function (event) {
    focused = event.target.matches(":focus-visible");
    schedule();
  });
  root.addEventListener("focusout", function (event) {
    if (!root.contains(event.relatedTarget)) { focused = false; schedule(); }
  });
  document.addEventListener("visibilitychange", schedule);

  root.tabIndex = 0;
  root.setAttribute("aria-roledescription", "carousel");
  root.classList.add("is-carousel");
  renderToggle();
  show(0, false);
  void root.offsetWidth; // flush styles so the first state change is not animated
  root.classList.add("is-ready");
  schedule();
})();
