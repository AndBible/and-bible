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
package net.bible.sharedui.webview

import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.HostBackDispatcher
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

/**
 * Hardware Esc while the Bible text has the keyboard focus (#4003). The WKWebView hands the key to the page first, so
 * the page decides, as desktop's page-script route does (`DesktopInputScript.ESCAPE_PART`): a bubble-phase listener
 * reports an Esc the page did not use (no `preventDefault`), that is not part of an IME composition and that is not in
 * an editable (the note editors own Esc). [ReadingWkWebView.pressesBegan] swallows the native press, so the same Esc
 * cannot also bubble to Compose's scene input and go back a second time.
 */
internal const val READING_ESCAPE_HANDLER_NAME = "readingEscape"

internal const val READING_ESCAPE_SCRIPT = """
(function () {
  if (window.__abReadingEscape) return; window.__abReadingEscape = true;
  function editable(n) {
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
  }
  window.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape' || e.defaultPrevented || e.isComposing || e.keyCode === 229) return;
    if (editable(e.target)) return;
    try { window.webkit.messageHandlers.readingEscape.postMessage(''); } catch (x) {}
  }, false);
})();
"""

/** Runs the shared back for an Esc the page reported (WebKit calls this on the main thread). Never throws. */
@OptIn(ExperimentalForeignApi::class)
internal class IosReadingEscapeHandler(private val back: () -> Boolean = { HostBackDispatcher.back() }) :
    NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        try {
            back()
        } catch (t: Throwable) {
            nsLog("reading escape: back failed $t")
        }
    }
}

/** The one shared handler (stateless; WKUserContentController retains it). */
internal val sharedReadingEscapeHandler = IosReadingEscapeHandler()
