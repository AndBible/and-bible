// Scene runtime for website/video/render.py.
//
// render.py opens a scene as `scene.html?duration=…&speech=…&clip=…&format=…`
// and steps it frame by frame: `JB.seek(t)` puts every animation on the page
// at scene time `t` (seconds), then the page is screenshotted. Nothing runs on
// wall-clock time, so a frame is the same however slow the machine is.
//
// A scene registers its animations with `JB.scene(fn)`; `fn` gets the runtime
// and builds them with `JB.anim`. Times are seconds on the scene clock;
// `JB.at(x)` maps a fraction of the narration (0 = first word, 1 = last) to
// one, so animations follow the voice when the text is edited. `JB.word("some
// words")` is finer: the scene time that phrase starts being spoken, from
// Whisper's word timestamps (see render.py), for a highlight that must land on
// one word. In the short format every scene also gets burned-in captions built
// from the same timestamps; a scene opts out with `params.captions: false`, or lifts them with
// `params.caption_bottom` (a CSS length) when its own text sits low.
(() => {
  const q = new URLSearchParams(location.search);
  const num = (name, fallback) => {
    const value = Number(q.get(name));
    return Number.isFinite(value) && q.has(name) ? value : fallback;
  };
  const builders = [];
  const JB = {
    duration: num("duration", 6),
    speech: num("speech", 0.6),
    clip: num("clip", 4.8),
    format: q.get("format") || "main",
    params: JSON.parse(q.get("params") || "{}"),
    words: JSON.parse(q.get("words") || "[]").map(([text, start, end]) => ({ text, start, end })),
    groups: JSON.parse(q.get("groups") || "[]"),
    isReady: false,
    scene(fn) {
      builders.push(fn);
    },
    at(fraction) {
      return JB.speech + JB.clip * fraction;
    },
    // Scene time at which `phrase` starts being spoken (`end`: finishes). Throws if the narration
    // does not contain it, so a stale script fails the render rather than
    // drifting.
    word(phrase, { end = false } = {}) {
      const norm = (text) => text.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "");
      const wanted = phrase.split(/\s+/).map(norm).filter(Boolean);
      const spoken = JB.words.map((w) => norm(w.text));
      for (let i = 0; i + wanted.length <= spoken.length; i++) {
        if (wanted.every((w, j) => spoken[i + j] === w)) {
          const hit = end ? JB.words[i + wanted.length - 1].end : JB.words[i].start;
          return JB.speech + hit;
        }
      }
      throw new Error(`JB.word: "${phrase}" is not in the narration (${spoken.join(" ")})`);
    },
    // A moment given either way: a number is a fraction of the narration
    // (`at`), a string is the phrase whose first word starts it (`word`).
    when(x, options) {
      return typeof x === "string" ? JB.word(x, options) : JB.at(x);
    },
    // Animate `el` through `keyframes` between `from` and `to` (scene seconds).
    // `fill` is "both" for an element's first animation; a later one on the
    // same element must be "forwards", or its first keyframe would override
    // the earlier animation before the later one starts.
    anim(el, keyframes, from, to, easing = "cubic-bezier(.2,.7,.2,1)", fill = "both") {
      const targets = typeof el === "string" ? document.querySelectorAll(el) : [el];
      for (const target of targets) {
        const animation = target.animate(keyframes, {
          delay: from * 1000,
          duration: Math.max(1, (to - from) * 1000),
          fill,
          easing,
        });
        animation.pause();
      }
    },
    // Show a screenshot at `scale` times its natural size. An integer scale
    // doubles pixels rather than smoothing them, so terminal text stays crisp.
    zoom(img, scale) {
      img.style.width = `${img.naturalWidth * scale}px`;
      img.style.height = `${img.naturalHeight * scale}px`;
      img.style.imageRendering = Number.isInteger(scale) ? "pixelated" : "auto";
      img.dataset.scale = String(scale);
      return img;
    },
    // A highlight ring over part of a zoomed screenshot, in the screenshot's
    // own (1:1) pixels; `img` must sit in a positioned wrapper at its top left.
    ring(img, [x, y, w, h], { dim = false, pad = 6 } = {}) {
      const scale = Number(img.dataset.scale || 1);
      const ring = document.createElement("div");
      ring.className = dim ? "ring dim" : "ring";
      Object.assign(ring.style, {
        left: `${img.offsetLeft + x * scale - pad}px`,
        top: `${img.offsetTop + y * scale - pad}px`,
        width: `${w * scale + 2 * pad}px`,
        height: `${h * scale + 2 * pad}px`,
      });
      img.parentElement.append(ring);
      return ring;
    },
    // Show `el` from `from`, optionally hide it again at `to`.
    show(el, from, to = null, rise = 16) {
      JB.anim(el, [{ opacity: 0, transform: `translateY(${rise}px)` },
                   { opacity: 1, transform: "none" }], from, from + 0.45);
      if (to !== null) {
        JB.anim(el, [{ opacity: 1 }, { opacity: 0 }], to, to + 0.35, "ease-in", "forwards");
      }
    },
    seek(t) {
      for (const animation of document.getAnimations()) {
        animation.pause();
        animation.currentTime = t * 1000;
      }
    },
  };
  window.JB = JB;
  document.documentElement.dataset.format = JB.format;

  // One group of words at a time, the spoken word in amber. Everything is a
  // Web Animation, so JB.seek drives it like the rest of the page.
  function captions() {
    const layer = document.createElement("div");
    layer.className = "burned-captions";
    // A scene whose own text sits low can lift the captions clear of it.
    if (JB.params.caption_bottom) layer.style.setProperty("--caption-bottom", JB.params.caption_bottom);
    document.body.append(layer);
    JB.groups.forEach((group, index) => {
      const el = document.createElement("div");
      el.className = "burned-caption";
      const first = JB.speech + group[0][1];
      const last = JB.speech + group[group.length - 1][2];
      const next = JB.groups[index + 1];
      const end = next ? Math.min(JB.speech + next[0][1], last + 0.3) : last + 0.3;
      for (const [text, start, stop] of group) {
        const span = document.createElement("span");
        span.textContent = text;
        el.append(span);
        const from = JB.speech + start;
        const idle = { color: "var(--text)", backgroundColor: "rgba(240, 169, 43, 0)" };
        const spoken = { color: "var(--ground)", backgroundColor: "var(--amber)" };
        JB.anim(span, [idle, spoken], from, from + 0.06, "ease-out");
        JB.anim(span, [spoken, idle], Math.max(from + 0.06, JB.speech + stop),
                JB.speech + stop + 0.06, "ease-in", "forwards");
      }
      layer.append(el);
      // Hard cuts, ending exactly where the next group starts, so two groups
      // are never on screen together.
      JB.anim(el, [{ opacity: 0 }, { opacity: 1 }], first, first + 0.04, "linear");
      JB.anim(el, [{ opacity: 1 }, { opacity: 0 }], end - 0.04, end, "linear", "forwards");
    });
  }

  window.addEventListener("load", async () => {
    await document.fonts.ready;
    await Promise.all(
      [...document.images].map((img) => (img.decode ? img.decode().catch(() => {}) : null)),
    );
    for (const fn of builders) fn(JB);
    if (JB.format === "short" && JB.params.captions !== false && JB.groups.length) captions();
    JB.seek(0);
    JB.isReady = true;
  });
})();
