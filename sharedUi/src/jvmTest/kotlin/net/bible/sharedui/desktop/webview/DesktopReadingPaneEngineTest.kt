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

import net.bible.sharedcore.bibleview.UnimplementedBibleBridge
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedui.desktop.webengine.FakeWebEngine
import net.bible.sharedui.desktop.webengine.androidBridgeShim
import net.bible.sharedui.webview.activeLanguagesScript
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopReadingPaneEngineTest {
    private val fake = FakeWebEngine()
    private val logs = mutableListOf<String>()
    private val engine = DesktopReadingPaneEngine(fake, { logs += it }, lang = { "fi" })
    private val ready = mutableListOf<String>()
    private val attached = mutableListOf<String>()

    private val bridge = object : UnimplementedBibleBridge({}, {}) {
        override fun setClientReady() { ready += "ready" }
    }

    private fun attach(id: String, dark: Boolean = false) {
        engine.jsHostFor(id)
        engine.attach(id, dark, bridge, respondNull = {}, onAttached = { attached += id })
    }

    private val readyMessage = """{"method":"setClientReady","args":[]}"""

    @Test
    fun samePaneForSameWindowId() {
        attach("w1")
        val first = engine.paneFor("w1")
        attach("w1")
        attach("w2")
        assertEquals(2, fake.panes.size)
        assertSame(first, engine.paneFor("w1"))
        assertNotSame(engine.paneFor("w1"), engine.paneFor("w2"))
        assertEquals(listOf("w1", "w2"), attached, "the channel is flushed once per pane")
        assertTrue(fake.pane("w1").url.contains("lang=fi&night=false"), fake.pane("w1").url)
        engine.release("w1")
        assertTrue(fake.pane("w1").disposed)
        assertEquals(null, engine.paneFor("w1"))
    }

    @Test
    fun themeChangeSetsBackgroundAndResendsConfig() {
        val night = 0xFF121212.toInt()
        val host = engine.jsHostFor("w1")
        engine.applyBackground("w1", night) // before the pane exists: remembered
        attach("w1", dark = true)
        assertTrue(fake.pane("w1").url.endsWith("night=true"))
        assertEquals(listOf(night), fake.pane("w1").backgrounds, "a remembered background is applied at creation")
        // What the hoisted relay does on a theme change: paint through onPageBackground, then re-send set_config.
        val day = 0xFFFFFFFF.toInt()
        engine.applyBackground("w1", day)
        host.evaluate("bibleView.emit('set_config', {})", null)
        assertEquals(listOf(night, day), fake.pane("w1").backgrounds)
        assertEquals(listOf("bibleView.emit('set_config', {})"), fake.pane("w1").evaluated)
        engine.applyBackground("unknown", day) // ignored
    }

    @Test
    fun jsPostedBeforeThePaneExistsRunsOnceItDoes() {
        val host = engine.jsHostFor("w1")
        assertFalse(host.isAttached)
        host.evaluate("early()", null)
        host.post { fake.panes.firstOrNull()?.evaluated?.add("posted") }
        // Queued through invokeLater when off the event thread; drain the event queue.
        javax.swing.SwingUtilities.invokeAndWait {}
        attach("w1")
        assertTrue(host.isAttached)
        assertEquals(listOf("early()", "posted"), fake.pane("w1").evaluated)
    }

    @Test
    fun activeLanguagesReachThePageOnEveryLoad() {
        engine.setActiveLanguages { """["fi","en"]""" }
        assertEquals("""["fi","en"]""", fake.languagesSource!!.invoke())
        val host = engine.jsHostFor("w1")
        attach("w1")
        // The hoisted assembly evaluates this before each document load; the shim answers it live.
        host.evaluate(activeLanguagesScript("""["fi","sv"]"""), null)
        assertEquals("window.__activeLanguages__ = '[\"fi\",\"sv\"]';", fake.pane("w1").evaluated.single())
        val shim = androidBridgeShim("p", """["en"]""")
        assertTrue("window.__activeLanguages__" in shim && "[\"en\"]" in shim, "live global first, creation-time list as fallback: $shim")
        assertTrue(shim.contains("typeof window.__activeLanguages__ === 'string'"))
    }

    @Test
    fun crashedPaneIsRecreatedAndReloaded() {
        attach("w1")
        engine.applyBackground("w1", 0xFF000000.toInt())
        val crashed = fake.pane("w1")
        crashed.crash()
        assertTrue(crashed.disposed)
        assertEquals(2, fake.panes.size)
        val fresh = fake.live("w1")
        assertNotSame(crashed, fresh)
        assertEquals(crashed.url, fresh.url, "the same page is loaded again")
        assertSame(fresh, engine.paneFor("w1"))
        assertEquals(listOf(0xFF000000.toInt()), fresh.backgrounds)
        assertEquals(listOf("w1", "w1"), attached, "the channel is flushed for the new pane")
        // The new pane carries the bridge: its ready message reaches the bridge and is logged again.
        fresh.onMessage(readyMessage)
        assertEquals(listOf("ready"), ready)
        crashed.crash() // a stale notification of the disposed pane changes nothing
        assertEquals(2, fake.panes.size)
    }

    @Test
    fun readyLogLineIsWrittenOncePerPane() {
        attach("w1")
        fake.pane("w1").onMessage(readyMessage)
        fake.pane("w1").onMessage(readyMessage)
        assertEquals(listOf("[desktop] reading ready w1"), logs.filter { it.startsWith("[desktop]") })
        assertEquals(listOf("ready", "ready"), ready)
    }

    @Test
    fun aFailingPaneCreationParksTheWindow() {
        val failing = object : net.bible.sharedui.desktop.webengine.DesktopWebEngine by fake {
            override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit) =
                error("no natives")
        }
        val e = DesktopReadingPaneEngine(failing, { logs += it })
        e.attach("w1", false, bridge, {}, {})
        assertTrue(e.hasFailed("w1"))
        assertEquals(null, e.paneFor("w1"))
        assertTrue(logs.any { "no natives" in it }, logs.toString())
    }

    @Test
    fun displayModeSignalFiresOnNightModeStoreChange() {
        val settings = DesktopDeviceSettings(Files.createTempDirectory("night").resolve("prefs.properties"))
        val signals = DesktopDisplayModeSignals(net.bible.sharedui.desktop.assembly.DesktopNightModeStub(settings), runOnMain = { it() })
        var fired = 0
        val stop = signals.observe { fired++ }
        try {
            settings.setString("night_mode_pref3", "manual")
            assertEquals(1, fired)
            settings.setString("night_mode_pref3", "manual") // same value
            settings.setString("unrelated", "x")
            assertEquals(1, fired)
            settings.setBoolean("night_mode_pref", true)
            assertEquals(2, fired)
        } finally {
            stop()
        }
        settings.setBoolean("night_mode_pref", false)
        assertEquals(2, fired, "stopped observers hear nothing")
    }

    @Test
    fun rendererDeathCallsTheOwnerOnceAndIgnoresStalePanes() {
        val gone = mutableListOf<String>()
        val e = DesktopReadingPaneEngine(fake, { logs += it }, lang = { "en" }, onRendererGone = { gone += it })
        e.attach("w1", false, bridge, {}, {})
        val first = fake.pane("w1")
        first.crash()
        assertEquals(listOf("w1"), gone)
        assertTrue(first.disposed)
        assertEquals(null, e.paneFor("w1"), "the owner decides when a new pane is attached")
        assertEquals(1, fake.panes.size, "the engine does not recreate on its own")
        first.crash() // the dead pane again
        assertEquals(listOf("w1"), gone)
    }

    @Test
    fun ownerRecoveryReleasesAndAttachesAFreshPane() {
        lateinit var e: DesktopReadingPaneEngine
        val recovery = DesktopReadingRecovery(
            release = { e.release(it) },
            isOpen = { true },
            assemble = { e.jsHostFor(it) },
            reload = { e.attach(it, false, bridge, {}, { attached += "re:$it" }) },
        )
        e = DesktopReadingPaneEngine(fake, { logs += it }, onRendererGone = recovery)
        e.attach("w1", false, bridge, {}, {})
        fake.pane("w1").crash()
        val fresh = fake.live("w1")
        assertSame(fresh, e.paneFor("w1"))
        assertEquals(listOf("re:w1"), attached)
    }

    @Test
    fun recoveryOfAClosedWindowOnlyReleases() {
        val calls = mutableListOf<String>()
        DesktopReadingRecovery({ calls += "release" }, { false }, { calls += "assemble" }, { calls += "reload" })("w1")
        assertEquals(listOf("release"), calls)
        calls.clear()
        DesktopReadingRecovery({ calls += "release" }, { true }, { calls += "assemble" }, { calls += "reload" })("w1")
        assertEquals(listOf("release", "assemble", "reload"), calls)
    }

    @Test
    fun aCrashLoopParksTheWindow() {
        var gone = 0
        lateinit var e: DesktopReadingPaneEngine
        e = DesktopReadingPaneEngine(fake, { logs += it }, onRendererGone = {
            gone++
            e.release(it)
            e.attach(it, false, bridge, {}, {})
        })
        e.attach("w1", false, bridge, {}, {})
        repeat(DesktopReadingPaneEngine.MAX_RENDERER_DEATHS + 1) { if (!e.hasFailed("w1")) fake.live("w1").crash() }
        assertEquals(DesktopReadingPaneEngine.MAX_RENDERER_DEATHS, gone)
        assertTrue(e.hasFailed("w1"))
        assertEquals(null, e.paneFor("w1"))
        assertTrue(logs.any { "giving up" in it })
    }

    @Test
    fun readingInputAddsScriptsConsumesMessagesAndHearsEveryPane() {
        val events = mutableListOf<String>()
        val input = object : DesktopReadingInput {
            override fun documentStartScripts(windowId: String) = listOf("selection($windowId)")
            override fun handleMessage(windowId: String, text: String) = (text == "selection").also { if (it) events += "consumed" }
            override fun onPaneCreated(windowId: String, pane: net.bible.sharedui.desktop.webengine.DesktopWebPane) { events += "created:$windowId" }
            override fun onPaneDisposed(windowId: String, pane: net.bible.sharedui.desktop.webengine.DesktopWebPane) { events += "disposed:$windowId" }
        }
        engine.jsHostFor("w1")
        engine.attach("w1", false, bridge, {}, {}, input)
        val first = fake.pane("w1")
        assertEquals(listOf("selection(w1)"), first.scripts)
        first.onMessage("selection")
        first.onMessage(readyMessage)
        assertEquals(listOf("ready"), ready, "only the unconsumed message reaches the bridge")
        first.crash() // no owner: the engine recreates the pane with the same input
        assertEquals(listOf("selection(w1)"), fake.live("w1").scripts)
        engine.release("w1")
        assertEquals(listOf("created:w1", "consumed", "disposed:w1", "created:w1", "disposed:w1"), events)
    }

    @Test
    fun aThrowingReadingInputDoesNotBreakThePane() {
        val input = object : DesktopReadingInput {
            override fun onPaneCreated(windowId: String, pane: net.bible.sharedui.desktop.webengine.DesktopWebPane) = error("hook")
            override fun onPaneDisposed(windowId: String, pane: net.bible.sharedui.desktop.webengine.DesktopWebPane) = error("hook")
        }
        engine.jsHostFor("w1")
        engine.attach("w1", false, bridge, {}, { attached += "w1" }, input)
        assertEquals(listOf("w1"), attached)
        assertFalse(engine.hasFailed("w1"))
        engine.release("w1")
        assertTrue(fake.pane("w1").disposed)
    }

    @Test
    fun surfaceSharesAndCopiesThroughTheKoinPorts() {
        val calls = mutableListOf<String>()
        var canShare = false
        val app = org.koin.dsl.koinApplication {
            modules(org.koin.dsl.module {
                single<net.bible.sharedui.app.ports.TextShare> {
                    object : net.bible.sharedui.app.ports.TextShare {
                        override fun shareText(text: String, chooserTitle: String) { calls += "share:$text" }
                    }
                }
                single<net.bible.sharedui.app.ports.ClipboardText> {
                    object : net.bible.sharedui.app.ports.ClipboardText {
                        override fun read(): String? = null
                        override fun write(label: String, text: String) { calls += "copy:$text" }
                    }
                }
                single<net.bible.sharedui.desktop.assembly.DesktopFileShare> {
                    object : net.bible.sharedui.desktop.assembly.DesktopFileShare {
                        override val canShare: Boolean get() = canShare
                        override fun shareFile(path: String) { calls += "file:" + java.nio.file.Path.of(path).fileName }
                    }
                }
            })
        }
        try {
            val e = DesktopReadingPaneEngine(fake, { logs += it }, koin = { app.koin })
            val links = object : net.bible.sharedui.app.ports.ExternalLinks {
                override val askBeforeOpening: Boolean get() = false
                override fun open(url: String) = Unit
            }
            val surface = e.surfaceFor(fake.createPane("w1", "u", emptyList()) {}, links)
            surface.copyText(null, "a")
            surface.shareText("b", null)
            surface.shareHtml("<p/>", "t")
            assertEquals(listOf("copy:a", "share:b"), calls, "no file share while the port says it cannot share")
            canShare = true
            surface.shareHtml("<p/>", "t")
            assertEquals(listOf("copy:a", "share:b", "file:t.html"), calls)
        } finally {
            app.close()
        }
    }
}
