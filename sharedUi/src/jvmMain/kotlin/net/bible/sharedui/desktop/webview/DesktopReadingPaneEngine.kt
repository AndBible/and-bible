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
package net.bible.sharedui.desktop.webview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.bibleview.parseBridgeMessage
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webengine.kcef.SwingMainDispatcher
import net.bible.sharedui.host.reading.ReadingPaneEngine
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.webview.IosReadingWindowSurface
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

/**
 * The desktop [ReadingPaneEngine]: one [DesktopWebPane] per reading window over a [DesktopWebEngine], cached by window id.
 *
 * Shape: [jsHostFor] must answer before the pane can exist (the pane needs the bridge's message handler at creation),
 * so it returns a per-window [JsHost] that queues work until [attach] creates the pane (first composition of the pane
 * factory for an open window). [attach] also flushes the session's JS channel, as Android's and iOS's attach does.
 *
 * - Background: [applyBackground] is remembered per window and re-applied to a recreated pane; the pane itself
 *   re-applies it after each page load.
 * - Active languages: handed to the engine ([DesktopWebEngine.setActiveLanguages]) for the creation-time shim answer;
 *   the per-load `window.__activeLanguages__` refresh of the hoisted assembly is honoured live by the shim.
 * - Crash: a pane whose renderer died is disposed and recreated at the same URL (which reloads the page); the page's
 *   client-ready handshake then re-initialises the session.
 * - Readiness: the first `setClientReady` of each pane is logged as `[desktop] reading ready <windowId>`.
 *
 * Main (Swing event) thread, except [applyBackground] and the [JsHost] of a window.
 */
class DesktopReadingPaneEngine(
    private val engine: DesktopWebEngine,
    private val log: (String) -> Unit,
    private val lang: () -> String = { Locale.getDefault().language.ifEmpty { "en" } },
) : ReadingPaneEngine {
    private class Entry(val windowId: String) {
        /** The current pane; state so that the pane composable follows a crash recreation. */
        var pane by mutableStateOf<DesktopWebPane?>(null)
        val pending = mutableListOf<() -> Unit>()
        var wiring: Wiring? = null

        @Volatile
        var background: Int? = null
        var failed = false
        var readyLogged = false
    }

    private class Wiring(val dark: Boolean, val dispatcher: BridgeDispatcher, val onAttached: () -> Unit)

    private val entries = ConcurrentHashMap<String, Entry>()

    override fun setActiveLanguages(source: () -> String) = engine.setActiveLanguages(source)

    override fun jsHostFor(windowId: String): JsHost = DeferredJsHost(entry(windowId))

    override fun surfaceFor(host: JsHost, links: ExternalLinks): ReadingWindowSurface = IosReadingWindowSurface(
        host = host,
        links = links,
        // Share, file share and the selection menu are D3a; until then text "shares" onto the clipboard and files do not share.
        textShare = object : TextShare {
            override fun shareText(text: String, chooserTitle: String) = copyToClipboard(text)
        },
        shareFile = { log("share of a file is not supported on the desktop yet: $it") },
        canShare = { false },
        tempDir = { System.getProperty("java.io.tmpdir") },
        copyToClipboard = { copyToClipboard(it) },
        writeSharedHtml = { dir, path, html -> writeSharedHtml(dir, path, html) },
    )

    override fun applyBackground(windowId: String, argb: Int) {
        val e = entries[windowId] ?: return
        e.background = argb
        e.pane?.setBackground(argb)
    }

    override fun release(windowId: String) {
        val e = entries.remove(windowId) ?: return
        e.pane?.dispose()
        e.pane = null
        e.wiring = null
        e.pending.clear()
    }

    /** The window's current pane (null before [attach]); read in composition, it follows a crash recreation. */
    fun paneFor(windowId: String): DesktopWebPane? = entries[windowId]?.pane

    /** Whether [attach] failed for the window: its pane stays empty and is not retried. */
    fun hasFailed(windowId: String): Boolean = entries[windowId]?.failed == true

    /**
     * Creates the window's pane once, loads the bundle ([dark] is only the initial `night` URL parameter; live night
     * mode travels in `set_config`), wires the page's bridge messages to [bridge] and runs [onAttached] (the session's
     * `jsChannel.flush()`). A second call, or a call after a failure, does nothing; a failure is logged and parks the pane.
     * [respondNull] answers a deferred call whose handler threw.
     */
    fun attach(windowId: String, dark: Boolean, bridge: BibleBridge, respondNull: (Long) -> Unit, onAttached: () -> Unit) {
        val e = entry(windowId)
        if (e.pane != null || e.failed) return
        val paneLog: (String) -> Unit = { msg -> log("bridge[$windowId]: $msg") }
        e.wiring = Wiring(dark, BridgeDispatcher(bridge, respondNull, paneLog), onAttached)
        try {
            create(e)
        } catch (t: Throwable) {
            e.failed = true
            e.wiring = null
            log("reading pane wiring failed for $windowId: $t")
        }
    }

    /** Parks the window's pane as failed (wiring threw before [attach]); the owner logs nothing else. */
    fun markFailed(windowId: String, cause: Throwable) {
        entry(windowId).failed = true
        log("reading pane wiring failed for $windowId: $cause")
    }

    private fun create(e: Entry) {
        val w = checkNotNull(e.wiring)
        e.readyLogged = false
        val pane = engine.createPane(e.windowId, engine.bundleUrl(lang(), w.dark), emptyList()) { text -> onMessage(e, text) }
        pane.setOnRendererGone { rendererGone(e, pane) }
        e.background?.let(pane::setBackground)
        e.pane = pane
        val queued = e.pending.toList()
        e.pending.clear()
        queued.forEach { pane.post(it) }
        w.onAttached()
    }

    private fun onMessage(e: Entry, text: String) {
        try {
            val message = parseBridgeMessage(text).getOrNull()
            if (message?.method == "setClientReady" && !e.readyLogged) {
                e.readyLogged = true
                log("[desktop] reading ready ${e.windowId}")
            }
            val dispatcher = e.wiring?.dispatcher ?: return
            if (message != null) dispatcher.dispatch(message) else dispatcher.dispatch(text)
        } catch (t: Throwable) {
            log("bridge[${e.windowId}]: handler failure $t")
        }
    }

    private fun rendererGone(e: Entry, gone: DesktopWebPane) {
        if (e.pane !== gone || entries[e.windowId] !== e) return
        log("reading pane ${e.windowId}: renderer process gone, recreating")
        runCatching { gone.dispose() }
        e.pane = null
        try {
            create(e)
        } catch (t: Throwable) {
            e.failed = true
            log("reading pane ${e.windowId}: recreation failed $t")
        }
    }

    private fun entry(windowId: String): Entry = entries.getOrPut(windowId) { Entry(windowId) }

    /** The window's [JsHost]: the current pane once it exists; work posted before that is queued and run on creation. */
    private class DeferredJsHost(private val e: Entry) : JsHost {
        override val isMainThread: Boolean get() = SwingUtilities.isEventDispatchThread()
        override val isAttached: Boolean get() = e.pane?.isAttached == true
        override val mainDispatcher: CoroutineDispatcher get() = e.pane?.mainDispatcher ?: SwingMainDispatcher

        override fun post(block: () -> Unit) {
            val p = e.pane
            if (p != null) p.post(block) else SwingUtilities.invokeLater { e.pane?.post(block) ?: e.pending.add(block) }
        }

        override fun evaluate(js: String, callback: ((String) -> Unit)?) {
            val p = e.pane
            if (p != null) p.evaluate(js, callback) else SwingUtilities.invokeLater {
                e.pane?.evaluate(js, callback) ?: e.pending.add { e.pane?.evaluate(js, callback) }
            }
        }
    }

    private companion object {
        fun copyToClipboard(text: String) {
            SwingUtilities.invokeLater { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
        }

        fun writeSharedHtml(dir: String, path: String, html: String): Boolean = try {
            Files.createDirectories(Path.of(dir))
            Files.writeString(Path.of(path), html)
            true
        } catch (_: Exception) {
            false
        }
    }
}
