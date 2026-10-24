package net.bible.sharedui.poc

import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.bibleview.BridgeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PocBibleViewControllerTest {
    private val evaluated = mutableListOf<String>()
    private val logged = mutableListOf<String>()
    private val controller = PocBibleViewController("w1", """{"id":"poc-eph2"}""", darkTheme = true)
    private val bridge = controller.bridge({ evaluated += it.js }, { logged += it })
    private val dispatcher = BridgeDispatcher(bridge, { }, { })

    @Test fun clientReadySendsConfigThenDocument() {
        assertEquals(BridgeResult.Handled, dispatcher.dispatch("""{"method":"setClientReady","args":[]}"""))
        assertEquals(3, evaluated.size)
        assertTrue(evaluated[0].startsWith("bibleView.emit(\"set_config\", {"), evaluated[0])
        assertTrue(evaluated[0].contains("\"initial\":true"))
        assertTrue(evaluated[0].contains("\"nightMode\":true"))
        assertTrue(evaluated[0].contains("\"windowId\":\"w1\""))
        assertEquals("bibleView.emit(\"clear_document\")", evaluated[1])
        assertEquals("bibleView.emit(\"add_documents\", {\"id\":\"poc-eph2\"})", evaluated[2])
    }

    @Test fun clientReadyTwiceResendsEverything() {
        repeat(2) { dispatcher.dispatch("""{"method":"setClientReady","args":[]}""") }
        assertEquals(6, evaluated.size)
    }

    @Test fun scrollUpdatesVerse() {
        assertNull(controller.currentVerse.value)
        dispatcher.dispatch("""{"method":"scrolledToOrdinal","args":["KJVA:Eph.2",36301]}""")
        assertEquals(8, controller.currentVerse.value)
        assertEquals("Ephesians 2:8", controller.titleFor(8))
        assertEquals("Ephesians 2", controller.titleFor(null))
    }

    @Test fun scrollWithNullOrBadOrdinalIsIgnored() {
        dispatcher.dispatch("""{"method":"scrolledToOrdinal","args":["k",null]}""")
        dispatcher.dispatch("""{"method":"scrolledToOrdinal","args":["k",-1]}""")
        assertNull(controller.currentVerse.value)
    }

    @Test fun consoleIsLogged() {
        assertEquals(BridgeResult.Handled, dispatcher.dispatch("""{"method":"console","args":["error","x"]}"""))
        assertTrue(logged.any { it.contains("error") && it.contains("x") })
    }

    @Test fun unsupportedDeferredCallAnswersNull() {
        dispatcher.dispatch("""{"method":"parseRef","args":[9,"Gen 1"]}""")
        assertEquals(listOf("bibleView.response(9, null)"), evaluated)
    }
}
