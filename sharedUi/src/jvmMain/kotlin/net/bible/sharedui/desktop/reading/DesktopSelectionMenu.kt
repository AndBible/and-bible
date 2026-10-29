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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.bible.android.view.activity.page.SelectionMenuItem
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.host.reading.CachedSelection
import net.bible.sharedui.host.reading.IosReadingWindows
import net.bible.sharedui.host.reading.IosSelectionMenu
import net.bible.sharedui.host.reading.SelectionMenuRow

/**
 * AndBible's selection menu on a right-click in a desktop reading pane (D3a). The menu is a DOM menu drawn by the page at
 * the pointer ([PART]); this class is the Kotlin side. The logic is the hoisted [IosSelectionMenu] (same rows, labels and
 * actions as iOS).
 *
 * Messages (envelope of [DesktopReadingInputImpl]): `contextmenu` {x, y, selection} (CSS px; selection = JSON text of
 * `bibleView.querySelection()`), `menuItem` {item} (a [SelectionMenuItem] name), `menuClosed`. The menu is shown by
 * evaluating `window.__abMenu.show(rows, x, y, night)` on the window's pane (the pane [onPaneCreated] reported). The
 * snapshot the menu was built from is kept per window until the menu closes or an item ran, so a selection that
 * collapses meanwhile still counts.
 *
 * @param menu the shared menu logic, null while the reading assembly is not bound
 * @param nightMode whether to draw the dark variant
 */
class DesktopSelectionMenu(
    private val menu: () -> IosSelectionMenu? = { IosReadingWindows.selectionMenu },
    private val nightMode: () -> Boolean = { IosReadingWindows.nightMode },
) {
    private val panes = mutableMapOf<String, DesktopWebPane>()
    private val snapshots = mutableMapOf<String, CachedSelection>()

    fun onPaneCreated(windowId: String, pane: DesktopWebPane) {
        panes[windowId] = pane
    }

    fun onPaneDisposed(windowId: String, pane: DesktopWebPane) {
        if (panes[windowId] === pane) panes.remove(windowId)
        snapshots.remove(windowId)
    }

    /** Registers this menu's page part and message handlers on [input]. */
    fun install(input: DesktopReadingInputImpl) {
        input.scriptParts += PART
        input.extensions["contextmenu"] = ::onContextMenu
        input.extensions["menuItem"] = ::onMenuItem
        input.extensions["menuClosed"] = { windowId, _ -> snapshots.remove(windowId) }
    }

    internal fun onContextMenu(windowId: String, m: JsonObject) {
        val logic = menu() ?: return
        val pane = panes[windowId] ?: return
        val raw = when (val s = m["selection"]) {
            null, JsonNull -> "null"
            is JsonPrimitive -> if (s.isString) s.content else s.toString()
            else -> s.toString()
        }
        logic.cacheSelection(windowId, raw)
        val snapshot = logic.snapshot(windowId) ?: return
        val rows = logic.rows(windowId, snapshot)
        if (rows.isEmpty()) return
        snapshots[windowId] = snapshot
        val x = m["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val y = m["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val json = JsonArray(rows.map { row ->
            buildJsonObject {
                put("item", row.item.name)
                put("label", row.title)
                if (logic.opensStepTwo(windowId, row.item)) {
                    put("sub", rowsJson(logic.stepTwoRows(windowId, snapshot)))
                }
            }
        })
        pane.evaluate("window.__abMenu&&window.__abMenu.show(${safe(json)},$x,$y,${nightMode()});", null)
    }

    internal fun onMenuItem(windowId: String, m: JsonObject) {
        val logic = menu() ?: return
        val item = m["item"]?.jsonPrimitive?.contentOrNull?.let { n -> SelectionMenuItem.entries.firstOrNull { it.name == n } } ?: return
        val snapshot = snapshots[windowId] ?: return
        if (logic.opensStepTwo(windowId, item)) return // a parent row only opens its choices
        snapshots.remove(windowId)
        logic.run(windowId, item, snapshot)
    }

    private fun rowsJson(rows: List<SelectionMenuRow>): JsonElement = buildJsonArray {
        rows.forEach { add(buildJsonObject { put("item", it.item.name); put("label", it.title) }) }
    }

    // JSON is JavaScript; only the line separators were not before ES2019, escape them anyway.
    private fun safe(e: JsonElement) = Json.encodeToString(JsonElement.serializer(), e)
        .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    companion object {
        /**
         * The page part: bubble-phase `contextmenu` on window (skipped when the page handled it, or the selection is empty),
         * and `window.__abMenu`, the renderer. Escape is taken in the capture phase while the menu is open (and its keyup),
         * so neither the back navigation part nor page modals see it. Grayscale, system font, no animation.
         */
        const val PART: String = """
(function () {
  var menu = null, swallowUp = false, st = null;
  function close(notify) {
    if (!menu) return;
    if (menu.parentNode) menu.parentNode.removeChild(menu);
    menu = null;
    if (notify !== false) window.__abDesktop.post({type: 'menuClosed'});
  }
  function onOutside(e) { if (menu && !menu.contains(e.target)) close(); }
  function onKey(e) {
    if (e.isComposing) return;
    if (menu && e.key === 'Escape') {
      swallowUp = true; e.preventDefault(); e.stopImmediatePropagation(); close();
    }
  }
  function onKeyUp(e) {
    if (swallowUp && e.key === 'Escape') { swallowUp = false; e.preventDefault(); e.stopImmediatePropagation(); }
  }
  window.addEventListener('keydown', onKey, true);
  window.addEventListener('keyup', onKeyUp, true);
  window.addEventListener('mousedown', onOutside, true);
  // The menu's own scrolling (a tall menu) must not close it.
  function onScroll(e) { if (menu && !(e.target && e.target.nodeType && menu.contains(e.target))) close(); }
  window.addEventListener('wheel', onScroll, {passive: true, capture: true});
  window.addEventListener('scroll', onScroll, true);
  // No blur-close: KCEF blurs the page window while a pane is clicked and keeps the focus elsewhere.
  window.addEventListener('resize', function () { close(); });
  function place(x, y) {
    var r = menu.getBoundingClientRect(), w = window.innerWidth, h = window.innerHeight;
    menu.style.left = Math.max(0, Math.min(x, w - r.width)) + 'px';
    menu.style.top = Math.max(0, Math.min(y, h - r.height)) + 'px';
  }
  function button(row, night, indent, onClick) {
    var b = document.createElement('button');
    b.type = 'button';
    b.textContent = row.label;
    var s = b.style;
    s.display = 'block'; s.width = '100%'; s.textAlign = 'start'; s.border = '0'; s.margin = '0';
    s.padding = '6px 14px'; s.font = 'inherit'; s.cursor = 'default';
    s.background = 'transparent'; s.color = night ? '#e6e6e6' : '#1a1a1a'; s.whiteSpace = 'nowrap';
    s.paddingInlineStart = (14 + indent) + 'px';
    b.addEventListener('mouseenter', function () { s.background = night ? '#4a4a4a' : '#dcdcdc'; });
    b.addEventListener('mouseleave', function () { s.background = 'transparent'; });
    b.addEventListener('click', function (e) { e.preventDefault(); e.stopPropagation(); onClick(); });
    return b;
  }
  window.__abMenu = {
    show: function (rows, x, y, night) {
      close(false);
      var m = document.createElement('div');
      m.setAttribute('role', 'menu');
      var s = m.style;
      s.position = 'fixed'; s.left = '0px'; s.top = '0px'; s.zIndex = '2147483647'; s.minWidth = '160px';
      s.maxHeight = '100vh'; s.overflowY = 'auto'; s.padding = '4px 0'; s.boxSizing = 'border-box';
      s.font = '14px system-ui, -apple-system, "Segoe UI", sans-serif';
      s.background = night ? '#2b2b2b' : '#ffffff'; s.color = night ? '#e6e6e6' : '#1a1a1a';
      s.border = '1px solid ' + (night ? '#8a8a8a' : '#808080'); s.boxShadow = 'none';
      // The page's selection must survive a press on the menu.
      m.addEventListener('mousedown', function (e) { e.preventDefault(); });
      m.addEventListener('contextmenu', function (e) { e.preventDefault(); e.stopPropagation(); });
      rows.forEach(function (row) {
        if (row.sub) {
          var open = false, subs = [];
          var parent = button(row, night, 0, function () {
            open = !open;
            if (open) {
              row.sub.forEach(function (sr) {
                var sb = button(sr, night, 16, function () { choose(sr.item); });
                parent.parentNode.insertBefore(sb, (subs.length ? subs[subs.length - 1] : parent).nextSibling);
                subs.push(sb);
              });
            } else {
              subs.forEach(function (sb) { sb.parentNode.removeChild(sb); }); subs = [];
            }
            place(x, y);
          });
          m.appendChild(parent);
        } else {
          m.appendChild(button(row, night, 0, function () { choose(row.item); }));
        }
      });
      function choose(item) { close(false); window.__abDesktop.post({type: 'menuItem', item: item}); }
      document.body.appendChild(m);
      menu = m;
      place(x, y);
    },
    close: close
  };
  // The page's own contextmenu handlers (bubble, e.g. read history) win: they prevent the default first.
  window.addEventListener('contextmenu', function (e) {
    if (menu) close();
    if (e.defaultPrevented) return;
    try {
      var sel = window.getSelection ? window.getSelection() : null;
      if (!sel || sel.isCollapsed || !window.bibleView || !window.bibleView.querySelection) return;
      var q = window.bibleView.querySelection();
      if (q === null || q === undefined) return;
      e.preventDefault();
      window.__abDesktop.post({type: 'contextmenu', x: e.clientX, y: e.clientY, selection: JSON.stringify(q)});
    } catch (x) {}
  }, false);
})();
"""
    }
}
