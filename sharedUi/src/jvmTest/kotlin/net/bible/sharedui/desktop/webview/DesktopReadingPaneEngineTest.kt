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
        val signals = DesktopDisplayModeSignals(settings, runOnMain = { it() })
        var fired = 0
        val stop = signals.observe { fired++ }
        settings.setString("night_mode_pref3", "manual")
        assertEquals(1, fired)
        settings.setString("night_mode_pref3", "manual") // same value
        settings.setString("unrelated", "x")
        assertEquals(1, fired)
        settings.setBoolean("night_mode_pref", true)
        assertEquals(2, fired)
        stop()
        settings.setBoolean("night_mode_pref", false)
        assertEquals(2, fired, "stopped observers hear nothing")
    }
}
