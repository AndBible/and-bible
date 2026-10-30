/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.desktop.reading

/**
 * The page script of the desktop reading input, as parts that share one preamble. The script posts
 * `{"desktopInput": {"type": ..., ...}}` through `window.androidBridge.postMessage`; see [DesktopReadingInputImpl] for
 * the message types. A part is a self-contained statement list that may use `window.__abDesktop.post(obj)` (posts an
 * envelope) and `window.__abDesktop.isEditable(node)`; [compose] wraps the preamble and the parts, each in its own
 * try/catch, so a failing part never takes the others down.
 *
 * CEF holds the keyboard focus while a pane is used, so none of this reaches Kotlin any other way.
 */
object DesktopInputScript {
    /** Defines `window.__abDesktop`; idempotent guard in [compose]. */
    const val PREAMBLE: String = """
window.__abDesktop = {
  post: function (o) {
    try { window.androidBridge.postMessage(JSON.stringify({desktopInput: o})); } catch (e) {}
  },
  isEditable: function (n) {
    try {
      if (!n || n.nodeType !== 1) return false;
      var t = n.tagName;
      if (t === 'TEXTAREA' || t === 'SELECT') return true;
      if (t === 'INPUT') {
        var ty = (n.type || 'text').toLowerCase();
        return !(ty === 'button' || ty === 'checkbox' || ty === 'radio' || ty === 'submit' || ty === 'reset' || ty === 'range' || ty === 'color' || ty === 'image' || ty === 'file');
      }
      return !!n.isContentEditable;
    } catch (e) { return false; }
  },
  flags: function (e) {
    return {key: e.key, code: e.code, ctrl: !!(e.ctrlKey || e.metaKey), alt: !!e.altKey, shift: !!e.shiftKey};
  }
};
"""

    /**
     * Capture-phase keydown on window for the chords the page does not handle or that desktop overrides (they are
     * posted and cancelled). Letter chords match `e.code` (`KeyL`), as the page's keyboard.ts does, so non-Latin layouts
     * work. Inside an editable, Ctrl+C/F/W (what keyboard.ts handles on `document`) are kept from the page's document
     * handler by a one-shot listener on the target that stops propagation after the target's own handlers (an editor's
     * Ctrl+Z needs those); default actions are never prevented. Alt+arrows are not taken from a text field.
     */
    const val KEY_PART: String = """
var pendingStop = null;
function clearPendingStop() {
  if (pendingStop) { pendingStop.t.removeEventListener('keydown', pendingStop.f); pendingStop = null; }
}
window.addEventListener('keydown', function (e) {
  clearPendingStop();
  var d = window.__abDesktop, k = e.key;
  if (typeof k !== 'string' || e.isComposing || e.keyCode === 229) return;
  var ed = d.isEditable(e.target), ctrl = e.ctrlKey || e.metaKey;
  var letter = /^Key[A-Z]$/.test(e.code || '') ? e.code.charAt(3).toLowerCase() : null;
  var kk = letter !== null ? letter : (k.length === 1 ? k.toLowerCase() : k);
  if (ctrl && !e.altKey && ed && (kk === 'c' || kk === 'f' || kk === 'w') && e.target.addEventListener) {
    var t = e.target, f = function (ev) { ev.stopPropagation(); clearPendingStop(); };
    t.addEventListener('keydown', f);
    pendingStop = {t: t, f: f};
    setTimeout(clearPendingStop, 0);
    return;
  }
  var hit = false, selection = null;
  if (ctrl && !e.altKey) {
    if (kk === 'l' || kk === '+' || kk === '=' || kk === '-' || kk === '_' || kk === '0' || kk === 'Tab' || kk === ',') hit = true;
    else if (kk === 'b' && !ed) {
      try {
        var s = window.bibleView && window.bibleView.querySelection ? window.bibleView.querySelection() : null;
        var sel = window.getSelection ? window.getSelection() : null;
        if (s != null && sel && !sel.isCollapsed) { selection = JSON.stringify(s); hit = true; }
      } catch (x) {}
    }
  } else if (e.altKey && !ctrl) {
    if (!ed && (k === 'ArrowLeft' || k === 'ArrowRight')) hit = true;
  } else if (k === 'F11' && !ctrl && !e.altKey) hit = true;
  if (!hit) return;
  e.preventDefault(); e.stopPropagation();
  var o = d.flags(e); o.type = 'key'; o.key = kk.length === 1 ? kk : k; o.editable = ed; if (selection !== null) o.selection = selection;
  d.post(o);
}, true);
"""

    /** Bubble-phase Escape: skipped when the page used it (modals, editors), while composing, or in an editable. */
    const val ESCAPE_PART: String = """
window.addEventListener('keydown', function (e) {
  if (e.key !== 'Escape' || e.defaultPrevented || e.isComposing || e.keyCode === 229) return;
  var d = window.__abDesktop;
  if (d.isEditable(e.target)) return;
  var o = d.flags(e); o.type = 'key'; o.editable = false; d.post(o);
}, false);
"""

    /** The mouse back and forward buttons (DOM buttons 3 and 4): reported on mouseup, never left to Chromium's history. */
    const val MOUSE_PART: String = """
['mousedown', 'auxclick', 'mouseup'].forEach(function (name) {
  window.addEventListener(name, function (e) {
    if (e.button !== 3 && e.button !== 4) return;
    e.preventDefault(); e.stopPropagation();
    if (name === 'mouseup') window.__abDesktop.post({type: 'mouse', button: e.button});
  }, true);
});
"""

    /** Wheel deltas in pixels, coalesced per animation frame. Passive: Chromium scrolls the page itself. */
    const val WHEEL_PART: String = """
(function () {
  var dx = 0, dy = 0, pending = false;
  window.addEventListener('wheel', function (e) {
    if (e.ctrlKey) return;
    var m = e.deltaMode === 1 ? 16 : (e.deltaMode === 2 ? window.innerHeight : 1);
    dx += e.deltaX * m; dy += e.deltaY * m;
    if (pending) return;
    pending = true;
    requestAnimationFrame(function () {
      pending = false;
      var x = dx, y = dy; dx = 0; dy = 0;
      if (x !== 0 || y !== 0) window.__abDesktop.post({type: 'wheel', dx: x, dy: y});
    });
  }, {passive: true, capture: true});
})();
"""

    /**
     * Ctrl+wheel: Chromium would zoom the page, a second zoom path next to the font size shortcuts. Cancelled (hence
     * non-passive). The deltas accumulate (a trackpad pinch is many small events) and one zoom message is posted per
     * animation frame with `steps` whole steps of 100 px (a classic mouse notch of 100-120 px is one step) and the
     * direction in `dy`: negative (wheel up) = bigger. The remainder carries over.
     */
    const val ZOOM_PART: String = """
(function () {
  var acc = 0, pending = false, last = 0;
  window.addEventListener('wheel', function (e) {
    if (!e.ctrlKey) return;
    e.preventDefault();
    var dy = e.deltaY * (e.deltaMode === 1 ? 16 : (e.deltaMode === 2 ? window.innerHeight : 1)), now = performance.now();
    // The accumulator decays: a pause or a change of direction starts over.
    if (now - last > 300 || (acc !== 0 && (dy < 0) !== (acc < 0))) acc = 0;
    last = now;
    acc += dy;
    if (pending) return;
    pending = true;
    requestAnimationFrame(function () {
      pending = false;
      var steps = Math.floor(Math.abs(acc) / 100);
      if (steps < 1) return;
      var sign = acc < 0 ? -1 : 1;
      acc -= sign * steps * 100;
      window.__abDesktop.post({type: 'zoom', dy: sign, steps: steps});
    });
  }, {passive: false, capture: true});
})();
"""

    /** A click is a touch. The page's own click handling (verse selection) is untouched: no preventDefault. */
    const val CLICK_PART: String = """
window.addEventListener('click', function () { window.__abDesktop.post({type: 'click'}); }, {passive: true, capture: true});
"""

    /** The parts every pane gets. Task 3 adds the context menu part beside them. */
    val DEFAULT_PARTS: List<String> = listOf(KEY_PART, ESCAPE_PART, MOUSE_PART, WHEEL_PART, ZOOM_PART, CLICK_PART)

    /** The document-start script for [parts]: the preamble, then each part in its own try/catch. */
    fun compose(parts: List<String> = DEFAULT_PARTS): String = buildString {
        append("(function () {\nif (window.__abDesktopInstalled) return;\nwindow.__abDesktopInstalled = true;\n")
        append(PREAMBLE)
        for (p in parts) append("try {\n").append(p).append("\n} catch (e) {}\n")
        append("})();")
    }
}
